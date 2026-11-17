package tienda.integrador.orquestador.aplicacion;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import tienda.integrador.orquestador.aplicacion.Puertos.AtenderRespuesta;
import tienda.integrador.orquestador.aplicacion.Puertos.BandejaDeSalida;
import tienda.integrador.orquestador.aplicacion.Puertos.BandejaDeSalida.Orden;
import tienda.integrador.orquestador.aplicacion.Puertos.BandejaDeSalida.Resultado;
import tienda.integrador.orquestador.aplicacion.Puertos.ConsultarSagas;
import tienda.integrador.orquestador.aplicacion.Puertos.IniciarSaga;
import tienda.integrador.orquestador.aplicacion.Puertos.MensajesProcesados;
import tienda.integrador.orquestador.aplicacion.Puertos.RepositorioDeSagas;
import tienda.integrador.orquestador.aplicacion.Puertos.UnidadDeTrabajo;
import tienda.integrador.orquestador.aplicacion.Puertos.VigilarSagas;
import tienda.integrador.orquestador.dominio.Estado;
import tienda.integrador.orquestador.dominio.Saga;
import tienda.integrador.orquestador.dominio.Transiciones;
import tienda.integrador.orquestador.dominio.Transiciones.Paso;

/**
 * El orquestador como servicio de aplicación. LA LÓGICA (qué paso sigue) está en Transiciones, pura; aquí solo se coordinan puertos:
 * repositorio de sagas con versión, mensajes procesados y bandeja de salida, todos dentro de UNA unidad de trabajo por mensaje.
 * Sin Spring, sin SQL, sin Kafka.
 */
public final class ServicioDeSagas implements IniciarSaga, AtenderRespuesta, VigilarSagas, ConsultarSagas {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ServicioDeSagas.class);
    public static final int REINTENTOS_ANTES_DE_VENCER = 3;

    private final RepositorioDeSagas sagas;
    private final MensajesProcesados procesados;
    private final BandejaDeSalida bandeja;
    private final UnidadDeTrabajo unidad;
    private final Transiciones transiciones = new Transiciones(false);
    private final Duration espera;
    private final int intentosDeCompensacion;
    public final AtomicInteger conflictos = new AtomicInteger();

    public ServicioDeSagas(RepositorioDeSagas sagas, MensajesProcesados procesados, BandejaDeSalida bandeja, UnidadDeTrabajo unidad,
                           Duration espera, int intentosDeCompensacion) {
        this.sagas = sagas;
        this.procesados = procesados;
        this.bandeja = bandeja;
        this.unidad = unidad;
        this.espera = espera;
        this.intentosDeCompensacion = intentosDeCompensacion;
    }

    /** Crea la saga y su primera orden en una transacción. Idempotente por id de saga. */
    @Override
    public void iniciar(Inicio i) {
        unidad.ejecutar(() -> {
            Paso inicio = transiciones.inicio();
            Saga nueva = new Saga(i.sagaId(), i.correlacion(), inicio.nuevoEstado(), 0, i.centavos(), i.producto(), i.cantidad(), i.ciudad(),
                    inicio.orden(), inicio.destino(), 0, null, false);
            if (!sagas.crear(nueva)) return null;
            bandeja.enviarOrden(inicio.destino(), new Orden(i.sagaId() + "/" + inicio.orden(), i.sagaId(), i.correlacion(), inicio.orden(), i.centavos(), i.producto(), i.cantidad(), i.ciudad()));
            return null;
        });
    }

    /** Atiende una respuesta. Si otra instancia cambió la saga entre la lectura y la escritura, relee y repite. */
    @Override
    public void atender(Respuesta respuesta) {
        for (int intento = 1; ; intento++) {
            try {
                unidad.ejecutar(() -> aplicar(respuesta));
                return;
            } catch (ConflictoDeVersion c) {
                conflictos.incrementAndGet();                        // se descarta todo lo hecho en la unidad y se vuelve a leer
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
        if (!procesados.marcarNuevo(r.id())) return false;                                    // repetida
        Optional<Saga> encontrada = sagas.buscar(r.sagaId());
        if (encontrada.isEmpty()) return false;
        Saga s = encontrada.get();
        Optional<Paso> paso = transiciones.siguiente(s.estado(), r.tipo());
        if (paso.isEmpty()) return false;                            // fuera de lugar (respuesta tardía o repetida de otra orden): se ignora
        Paso p = paso.get();
        Saga nueva = s.conPaso(p, motivoDe(r.tipo()));
        if (!sagas.actualizar(nueva, s.version())) throw new ConflictoDeVersion();            // optimista: alguien llegó antes
        LOG.info("correlacion={} saga={} {} --{}--> {}", s.correlacion(), s.id(), s.estado(), r.tipo(), p.nuevoEstado());
        if (p.nuevoEstado().terminal()) {                                                     // el resultado, para quien reaccione (coreografía)
            bandeja.anunciarResultado(new Resultado(s.id(), s.correlacion(), p.nuevoEstado() == Estado.COMPLETADA, nueva.motivo() == null ? "" : nueva.motivo()));
        }
        if (p.orden() != null) {
            bandeja.enviarOrden(p.destino(), new Orden(s.id() + "/" + p.orden(), s.id(), s.correlacion(), p.orden(), s.centavos(), s.producto(), s.cantidad(), s.ciudad()));
        }
        return true;
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
    @Override
    public void vigilar() {
        try {
            for (String id : sagas.idsSinRespuesta(espera, 50)) {
                unidad.ejecutar(() -> {
                    reintentarOVencer(id);
                    return null;
                });
            }
        } catch (RuntimeException ignorada) {
            // base reiniciándose
        }
    }

    private void reintentarOVencer(String id) {
        Optional<Saga> tomada = sagas.tomarParaReintento(id, espera);
        if (tomada.isEmpty()) return;                                // otra instancia se ocupa, o llegó una respuesta
        Saga s = tomada.get();
        int intentos = s.intentos() + 1;
        Optional<Paso> vencimiento = transiciones.alVencer(s.estado());
        if (intentos >= REINTENTOS_ANTES_DE_VENCER && vencimiento.isPresent()) {
            Paso p = vencimiento.get();
            sagas.cambiarPorVencimiento(s.conPaso(p, null), "sin respuesta a tiempo");
            bandeja.enviarOrden(p.destino(), new Orden(id + "/" + p.orden(), id, s.correlacion(), p.orden(), s.centavos(), s.producto(), s.cantidad(), s.ciudad()));
            return;
        }
        // Reintento: la MISMA orden (mismo id), para que el participante la reconozca. Tras demasiados intentos de una compensación, revisión humana.
        boolean compensando = s.estado() == Estado.LIBERANDO || s.estado() == Estado.REEMBOLSANDO;
        sagas.registrarIntento(id, intentos, compensando && intentos >= intentosDeCompensacion);
        bandeja.enviarOrden(s.destino(), new Orden(id + "/" + s.ordenPendiente(), id, s.correlacion(), s.ordenPendiente(), s.centavos(), s.producto(), s.cantidad(), s.ciudad()));
    }

    // ---- consultas ----

    @Override
    public Map<String, Long> porEstado() {
        return sagas.contarPorEstado();
    }

    @Override
    public List<Saga> noTerminadas() {
        return sagas.noTerminadas();
    }

    @Override
    public long vencidas(Duration mas) {
        return sagas.vencidas(mas);
    }

    @Override
    public long enRevision() {
        return sagas.enRevision();
    }
}
