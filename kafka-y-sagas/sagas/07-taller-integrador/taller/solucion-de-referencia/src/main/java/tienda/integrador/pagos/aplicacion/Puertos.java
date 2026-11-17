package tienda.integrador.pagos.aplicacion;

import java.util.Optional;
import java.util.function.Supplier;

/** Puertos del servicio. Ninguno menciona Kafka, SQL ni Spring. */
public final class Puertos {

    private Puertos() {
    }

    // ---- entrada ----

    public interface AtenderOrden {
        /** id estable de la orden: repetirla repite la respuesta guardada, sin repetir el efecto. */
        record Orden(String id, String sagaId, String correlacion, String tipo, long centavos, String producto, int cantidad, String ciudad) {
        }

        void atender(Orden orden);
    }

    // ---- salida ----

    /** La respuesta guardada de cada orden: una orden repetida repite la respuesta (la anterior pudo perderse). */
    public interface RegistroDeRespuestas {
        Optional<String> buscar(String ordenId);

        void guardar(String ordenId, String tipo);
    }

    public interface BandejaDeSalida {
        record Respuesta(String id, String sagaId, String correlacion, String tipo) {
        }

        void responder(Respuesta respuesta);
    }

    public interface UnidadDeTrabajo {
        <T> T ejecutar(Supplier<T> trabajo);
    }

    /** Los cobros. La clave de idempotencia es el id de la saga: un solo cobro por saga. */
    public interface Cobros {
        boolean hayLapida(String sagaId);

        void cobrar(String sagaId, long centavos);

        /** false si no había cobro que reembolsar. */
        boolean reembolsar(String sagaId);

        void ponerLapida(String sagaId);
    }
}
