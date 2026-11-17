package tienda.integrador.orquestador.aplicacion;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import tienda.integrador.orquestador.dominio.Saga;

/** Todos los puertos del orquestador. Ninguno menciona Kafka, SQL ni Spring. */
public final class Puertos {

    private Puertos() {
    }

    // ---- entrada ----

    /** Una compra nueva. Idempotente por id de saga. */
    public interface IniciarSaga {
        record Inicio(String sagaId, String correlacion, long centavos, String producto, int cantidad, String ciudad) {
        }

        void iniciar(Inicio inicio);
    }

    public interface AtenderRespuesta {
        /** id estable: la misma respuesta dos veces avanza la saga una sola vez. */
        record Respuesta(String id, String sagaId, String correlacion, String tipo) {
        }

        void atender(Respuesta respuesta);
    }

    /** El reloj: reenvía lo que no tiene respuesta y, tras los intentos, vence o pasa a revisión. */
    public interface VigilarSagas {
        void vigilar();
    }

    public interface ConsultarSagas {
        Map<String, Long> porEstado();

        List<Saga> noTerminadas();

        long vencidas(Duration mas);

        long enRevision();
    }

    // ---- salida ----

    public interface RepositorioDeSagas {
        /** false si ya existía. */
        boolean crear(Saga saga);

        Optional<Saga> buscar(String id);

        /** Guarda solo si la versión sigue siendo la que se leyó (control optimista). false = alguien llegó antes. */
        boolean actualizar(Saga nueva, long versionLeida);

        List<String> idsSinRespuesta(Duration espera, int limite);

        /** Bloquea la saga para reintentarla, si sigue sin respuesta y nadie más la tomó. */
        Optional<Saga> tomarParaReintento(String id, Duration espera);

        void registrarIntento(String id, int intentos, boolean revision);

        void cambiarPorVencimiento(Saga nueva, String motivo);

        Map<String, Long> contarPorEstado();

        List<Saga> noTerminadas();

        long vencidas(Duration mas);

        long enRevision();
    }

    public interface MensajesProcesados {
        /** true si es la primera vez que se ve este id. */
        boolean marcarNuevo(String id);
    }

    /** La bandeja de salida: se escribe en la misma unidad de trabajo que el cambio de estado. */
    public interface BandejaDeSalida {
        record Orden(String id, String sagaId, String correlacion, String tipo, long centavos, String producto, int cantidad, String ciudad) {
        }

        record Resultado(String sagaId, String correlacion, boolean completada, String motivo) {
        }

        void enviarOrden(String destino, Orden orden);

        void anunciarResultado(Resultado resultado);
    }

    public interface UnidadDeTrabajo {
        <T> T ejecutar(Supplier<T> trabajo);
    }
}
