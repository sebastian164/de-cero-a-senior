package tienda.integrador.orquestador.adaptadores.salida.jdbc;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tienda.integrador.orquestador.aplicacion.Puertos.BandejaDeSalida;
import tienda.integrador.orquestador.aplicacion.Puertos.MensajesProcesados;
import tienda.integrador.orquestador.aplicacion.Puertos.RepositorioDeSagas;
import tienda.integrador.orquestador.aplicacion.Puertos.UnidadDeTrabajo;
import tienda.integrador.orquestador.dominio.Estado;
import tienda.integrador.orquestador.dominio.Saga;
import tienda.integrador.plataforma.PublicadorDeSalida;

/** El adaptador de salida del orquestador sobre PostgreSQL: sagas con versión, mensajes procesados, tabla de salida y unidad de trabajo. */
public final class SagasJdbc implements RepositorioDeSagas, MensajesProcesados, BandejaDeSalida, UnidadDeTrabajo {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public SagasJdbc(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
        jdbc.execute("CREATE TABLE IF NOT EXISTS procesados(id text PRIMARY KEY)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS sagas(id text PRIMARY KEY, correlacion text, estado text NOT NULL, version bigint NOT NULL DEFAULT 0, centavos bigint, producto text, cantidad int, ciudad text, "
                + "orden_pendiente text, destino text, intentos int NOT NULL DEFAULT 0, ultimo_cambio timestamptz, motivo text, revision boolean NOT NULL DEFAULT false)");
    }

    // ---- UnidadDeTrabajo ----
    @Override
    public <T> T ejecutar(Supplier<T> trabajo) {
        return tx.execute(estado -> trabajo.get());
    }

    // ---- RepositorioDeSagas ----
    @Override
    public boolean crear(Saga s) {
        try {
            jdbc.update("INSERT INTO sagas(id, correlacion, estado, centavos, producto, cantidad, ciudad, orden_pendiente, destino, ultimo_cambio) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, clock_timestamp())",
                    s.id(), s.correlacion(), s.estado().name(), s.centavos(), s.producto(), s.cantidad(), s.ciudad(), s.ordenPendiente(), s.destino());
            return true;
        } catch (DuplicateKeyException yaExistia) {
            return false;
        }
    }

    private static Saga saga(Map<String, Object> f) {
        return new Saga((String) f.get("id"), f.get("correlacion") == null ? (String) f.get("id") : (String) f.get("correlacion"), Estado.valueOf((String) f.get("estado")),
                ((Number) f.get("version")).longValue(), ((Number) f.get("centavos")).longValue(), (String) f.get("producto"), ((Number) f.get("cantidad")).intValue(),
                (String) f.get("ciudad"), (String) f.get("orden_pendiente"), (String) f.get("destino"), ((Number) f.get("intentos")).intValue(), (String) f.get("motivo"),
                (Boolean) f.get("revision"));
    }

    @Override
    public Optional<Saga> buscar(String id) {
        return jdbc.queryForList("SELECT * FROM sagas WHERE id = ?", id).stream().findFirst().map(SagasJdbc::saga);
    }

    @Override
    public boolean actualizar(Saga n, long versionLeida) {
        return jdbc.update("UPDATE sagas SET estado = ?, version = version + 1, orden_pendiente = ?, destino = ?, intentos = 0, ultimo_cambio = clock_timestamp(), "
                + "motivo = ?, revision = false WHERE id = ? AND version = ?", n.estado().name(), n.ordenPendiente(), n.destino(), n.motivo(), n.id(), versionLeida) == 1;
    }

    @Override
    public List<String> idsSinRespuesta(Duration espera, int limite) {
        return jdbc.queryForList("SELECT id FROM sagas WHERE estado NOT IN ('COMPLETADA','CANCELADA') AND orden_pendiente IS NOT NULL "
                + "AND ultimo_cambio < clock_timestamp() - make_interval(secs => ?) ORDER BY id LIMIT ?", String.class, espera.toMillis() / 1000.0, limite);
    }

    @Override
    public Optional<Saga> tomarParaReintento(String id, Duration espera) {
        return jdbc.queryForList("SELECT * FROM sagas WHERE id = ? AND ultimo_cambio < clock_timestamp() - make_interval(secs => ?) FOR UPDATE SKIP LOCKED", id, espera.toMillis() / 1000.0)
                .stream().findFirst().map(SagasJdbc::saga);
    }

    @Override
    public void registrarIntento(String id, int intentos, boolean revision) {
        jdbc.update("UPDATE sagas SET intentos = ?, ultimo_cambio = clock_timestamp(), revision = ? WHERE id = ?", intentos, revision, id);
    }

    @Override
    public void cambiarPorVencimiento(Saga n, String motivoPorDefecto) {
        jdbc.update("UPDATE sagas SET estado = ?, version = version + 1, orden_pendiente = ?, destino = ?, intentos = 0, ultimo_cambio = clock_timestamp(), motivo = coalesce(motivo, ?) WHERE id = ?",
                n.estado().name(), n.ordenPendiente(), n.destino(), motivoPorDefecto, n.id());
    }

    @Override
    public Map<String, Long> contarPorEstado() {
        Map<String, Long> m = new TreeMap<>();
        jdbc.queryForList("SELECT estado, count(*) AS n FROM sagas GROUP BY estado").forEach(f -> m.put((String) f.get("estado"), ((Number) f.get("n")).longValue()));
        return m;
    }

    @Override
    public List<Saga> noTerminadas() {
        return jdbc.queryForList("SELECT * FROM sagas WHERE estado NOT IN ('COMPLETADA','CANCELADA') ORDER BY id").stream().map(SagasJdbc::saga).toList();
    }

    @Override
    public long vencidas(Duration mas) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM sagas WHERE estado NOT IN ('COMPLETADA','CANCELADA') AND ultimo_cambio < clock_timestamp() - make_interval(secs => ?)", Long.class,
                (double) mas.toSeconds());
        return n == null ? 0 : n;
    }

    @Override
    public long enRevision() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM sagas WHERE revision", Long.class);
        return n == null ? 0 : n;
    }

    // ---- MensajesProcesados ----
    @Override
    public boolean marcarNuevo(String id) {
        return jdbc.update("INSERT INTO procesados(id) VALUES (?) ON CONFLICT DO NOTHING", id) == 1;
    }

    // ---- BandejaDeSalida: el formato de los mensajes vive aquí, en el adaptador ----
    @Override
    public void enviarOrden(String destino, Orden o) {
        String json = "{\"id\":\"" + o.id() + "\",\"version\":1,\"sagaId\":\"" + o.sagaId() + "\",\"correlacion\":\"" + o.correlacion() + "\",\"tipo\":\"" + o.tipo()
                + "\",\"datos\":{\"centavos\":" + o.centavos() + ",\"producto\":\"" + o.producto() + "\",\"cantidad\":" + o.cantidad() + ",\"ciudad\":\"" + o.ciudad() + "\"}}";
        PublicadorDeSalida.poner(jdbc, "compra.ordenes." + destino, o.sagaId(), json);
    }

    @Override
    public void anunciarResultado(Resultado r) {
        String tipo = r.completada() ? "SagaCompletada" : "SagaCancelada";
        PublicadorDeSalida.poner(jdbc, "compra.resultados", r.sagaId(), "{\"id\":\"" + r.sagaId() + "/" + tipo + "\",\"version\":1,\"tipo\":\"" + tipo + "\",\"sagaId\":\"" + r.sagaId()
                + "\",\"correlacion\":\"" + r.correlacion() + "\",\"datos\":{\"motivo\":\"" + r.motivo() + "\"}}");
    }
}
