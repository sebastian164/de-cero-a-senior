package tienda;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import tienda.Mensajes.Orden;
import tienda.Mensajes.Respuesta;
import tienda.Transiciones.Paso;

/**
 * El orquestador sobre PostgreSQL: tabla de sagas con versión, tabla de mensajes procesados y tabla de salida.
 * La LÓGICA (qué paso sigue) está en Transiciones, pura. Aquí solo hay datos y transacciones.
 */
public class Orquestador {

    private static final Logger LOG = LoggerFactory.getLogger(Orquestador.class);

    public static final int REINTENTOS_ANTES_DE_VENCER = 3;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Transiciones transiciones = new Transiciones(false);
    private final long esperaMs;
    private final int intentosDeCompensacion;
    public final AtomicInteger conflictos = new AtomicInteger();
    /** Gancho de pruebas: se ejecuta entre leer la saga y guardarla (para forzar un conflicto de versión). */
    public volatile Runnable antesDeGuardar = () -> { };

    public Orquestador(JdbcTemplate jdbc, TransactionTemplate tx, long esperaMs, int intentosDeCompensacion) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.esperaMs = esperaMs;
        this.intentosDeCompensacion = intentosDeCompensacion;
    }

    /** Crea la saga y su primera orden en una transacción. Idempotente por id de saga. */
    public void iniciar(String sagaId, long centavos, String producto, int cantidad, String ciudad) {
        iniciar(sagaId, sagaId, centavos, producto, cantidad, ciudad);
    }

    public void iniciar(String sagaId, String correlacion, long centavos, String producto, int cantidad, String ciudad) {
        tx.executeWithoutResult(estado -> {
            Paso inicio = transiciones.inicio();
            try {
                jdbc.update("INSERT INTO sagas(id, correlacion, estado, centavos, producto, cantidad, ciudad, orden_pendiente, destino, ultimo_cambio) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, clock_timestamp())",
                        sagaId, correlacion, inicio.nuevoEstado().name(), centavos, producto, cantidad, ciudad, inicio.orden(), inicio.destino());
            } catch (DuplicateKeyException yaExistia) {
                return;
            }
            Salida.poner(jdbc, Mensajes.temaDeOrdenes(inicio.destino()), sagaId, new Orden(sagaId + "/" + inicio.orden(), sagaId, correlacion, inicio.orden(), centavos, producto, cantidad, ciudad).json());
        });
    }

    /** Atiende una respuesta. Si otra instancia cambió la saga entre la lectura y la escritura, relee y repite. */
    public void atender(Respuesta respuesta) {
        for (int intento = 1; ; intento++) {
            try {
                boolean aplicada = Boolean.TRUE.equals(tx.execute(estado -> aplicar(respuesta)));
                if (aplicada || intento > 0) return;
            } catch (ConflictoDeVersion c) {
                conflictos.incrementAndGet();                        // se descarta todo lo hecho en la transacción y se vuelve a leer
                if (intento >= 10) throw c;
            }
        }
    }

    static final class ConflictoDeVersion extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ConflictoDeVersion() {
            super("la saga cambió mientras se procesaba", null, false, false);
        }
    }

    private boolean aplicar(Respuesta r) {
        if (jdbc.update("INSERT INTO procesados(id) VALUES (?) ON CONFLICT DO NOTHING", r.id()) == 0) return false;          // repetida
        Map<String, Object> s = jdbc.queryForList("SELECT * FROM sagas WHERE id = ?", r.sagaId()).stream().findFirst().orElse(null);
        if (s == null) return false;
        Estado actual = Estado.valueOf((String) s.get("estado"));
        long version = ((Number) s.get("version")).longValue();
        Optional<Paso> paso = transiciones.siguiente(actual, r.tipo());
        antesDeGuardar.run();
        if (paso.isEmpty()) return false;                            // fuera de lugar (respuesta tardía o repetida de otra orden): se ignora
        Paso p = paso.get();
        int filas = jdbc.update("UPDATE sagas SET estado = ?, version = version + 1, orden_pendiente = ?, destino = ?, intentos = 0, ultimo_cambio = clock_timestamp(), "
                + "motivo = coalesce(?, motivo), revision = false WHERE id = ? AND version = ?",
                p.nuevoEstado().name(), p.orden(), p.destino(), motivoDe(r.tipo()), r.sagaId(), version);
        if (filas == 0) throw new ConflictoDeVersion();             // optimista: alguien llegó antes
        String correlacion = s.get("correlacion") == null ? r.sagaId() : (String) s.get("correlacion");
        LOG.info("correlacion={} saga={} {} --{}--> {}", correlacion, r.sagaId(), actual, r.tipo(), p.nuevoEstado());
        if (p.nuevoEstado().terminal()) {                                  // el resultado, para quien reaccione (coreografía): en la misma transacción
            String tipo = p.nuevoEstado() == Estado.COMPLETADA ? "SagaCompletada" : "SagaCancelada";
            Salida.poner(jdbc, Mensajes.RESULTADOS, r.sagaId(), "{\"id\":\"" + r.sagaId() + "/" + tipo + "\",\"version\":1,\"tipo\":\"" + tipo + "\",\"sagaId\":\"" + r.sagaId()
                    + "\",\"correlacion\":\"" + correlacion + "\",\"datos\":{\"motivo\":\"" + (motivoDe(r.tipo()) == null ? (s.get("motivo") == null ? "" : s.get("motivo")) : motivoDe(r.tipo())) + "\"}}");
        }
        if (p.orden() != null) {
            Orden o = new Orden(r.sagaId() + "/" + p.orden(), r.sagaId(), correlacion, p.orden(), ((Number) s.get("centavos")).longValue(), (String) s.get("producto"),
                    ((Number) s.get("cantidad")).intValue(), (String) s.get("ciudad"));
            Salida.poner(jdbc, Mensajes.temaDeOrdenes(p.destino()), r.sagaId(), o.json());
        }
        return true;
    }

    private static String corr(Map<String, Object> s, String id) {
        return s.get("correlacion") == null ? id : (String) s.get("correlacion");
    }

    private static String motivoDe(String tipo) {
        return switch (tipo) {
            case "sin-existencias" -> "sin existencias";
            case "pago-rechazado" -> "pago rechazado";
            case "envio-imposible" -> "envío imposible";
            default -> null;
        };
    }

    /** El reloj: reenvía la orden pendiente de las sagas sin respuesta y, tras REINTENTOS, vence (compensa) o marca para revisión. */
    @Scheduled(fixedDelay = 500)
    public void vigilar() {
        try {
            List<Map<String, Object>> sinRespuesta = jdbc.queryForList("SELECT id FROM sagas WHERE estado NOT IN ('COMPLETADA','CANCELADA') AND orden_pendiente IS NOT NULL "
                    + "AND ultimo_cambio < clock_timestamp() - make_interval(secs => ?) ORDER BY id LIMIT 50", esperaMs / 1000.0);
            for (Map<String, Object> f : sinRespuesta) {
                tx.executeWithoutResult(estado -> reintentarOVencer((String) f.get("id")));
            }
        } catch (RuntimeException ignorada) {
            // base reiniciándose
        }
    }

    private void reintentarOVencer(String id) {
        List<Map<String, Object>> filas = jdbc.queryForList("SELECT * FROM sagas WHERE id = ? AND ultimo_cambio < clock_timestamp() - make_interval(secs => ?) FOR UPDATE SKIP LOCKED", id, esperaMs / 1000.0);
        if (filas.isEmpty()) return;                                 // otra instancia se ocupa, o llegó una respuesta
        Map<String, Object> s = filas.get(0);
        Estado estado = Estado.valueOf((String) s.get("estado"));
        int intentos = ((Number) s.get("intentos")).intValue() + 1;
        Optional<Paso> vencimiento = transiciones.alVencer(estado);
        if (intentos >= REINTENTOS_ANTES_DE_VENCER && vencimiento.isPresent()) {
            Paso p = vencimiento.get();
            jdbc.update("UPDATE sagas SET estado = ?, version = version + 1, orden_pendiente = ?, destino = ?, intentos = 0, ultimo_cambio = clock_timestamp(), motivo = coalesce(motivo, 'sin respuesta a tiempo') WHERE id = ?",
                    p.nuevoEstado().name(), p.orden(), p.destino(), id);
            Salida.poner(jdbc, Mensajes.temaDeOrdenes(p.destino()), id, new Orden(id + "/" + p.orden(), id, corr(s, id), p.orden(), ((Number) s.get("centavos")).longValue(),
                    (String) s.get("producto"), ((Number) s.get("cantidad")).intValue(), (String) s.get("ciudad")).json());
            return;
        }
        // Reintento: la MISMA orden (mismo id), para que el participante la reconozca. Tras demasiados intentos de una compensación, revisión humana.
        boolean compensando = estado == Estado.LIBERANDO || estado == Estado.REEMBOLSANDO;
        boolean revision = compensando && intentos >= intentosDeCompensacion;
        jdbc.update("UPDATE sagas SET intentos = ?, ultimo_cambio = clock_timestamp(), revision = ? WHERE id = ?", intentos, revision, id);
        Salida.poner(jdbc, Mensajes.temaDeOrdenes((String) s.get("destino")), id, new Orden(id + "/" + s.get("orden_pendiente"), id, corr(s, id), (String) s.get("orden_pendiente"),
                ((Number) s.get("centavos")).longValue(), (String) s.get("producto"), ((Number) s.get("cantidad")).intValue(), (String) s.get("ciudad")).json());
    }

    // ---- consultas (paso 5 y métricas) ----

    public Map<String, Long> sagasPorEstado() {
        Map<String, Long> m = new java.util.TreeMap<>();
        jdbc.queryForList("SELECT estado, count(*) AS n FROM sagas GROUP BY estado").forEach(f -> m.put((String) f.get("estado"), ((Number) f.get("n")).longValue()));
        return m;
    }

    public List<Map<String, Object>> noTerminadas() {
        return jdbc.queryForList("SELECT id, estado, intentos, revision FROM sagas WHERE estado NOT IN ('COMPLETADA','CANCELADA') ORDER BY id");
    }

    public long vencidas(long segundos) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM sagas WHERE estado NOT IN ('COMPLETADA','CANCELADA') AND ultimo_cambio < clock_timestamp() - make_interval(secs => ?)", Long.class, (double) segundos);
        return n == null ? 0 : n;
    }

    public long enRevision() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM sagas WHERE revision", Long.class);
        return n == null ? 0 : n;
    }

    static Timestamp ahora() {
        return Timestamp.from(Instant.now());
    }
}
