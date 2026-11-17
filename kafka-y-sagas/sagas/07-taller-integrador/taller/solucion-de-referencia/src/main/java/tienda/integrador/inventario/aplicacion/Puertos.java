package tienda.integrador.inventario.aplicacion;

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

    /** Las existencias y sus reservas. descontar es atómico: nunca deja existencias negativas. */
    public interface Existencias {
        boolean hayLapida(String sagaId);

        /** true si había unidades suficientes y se descontaron. */
        boolean descontar(String producto, int cantidad);

        void registrarReserva(String sagaId, String producto, int cantidad);

        /** false si no había reserva vigente de esa saga. Si la había, devuelve las unidades. */
        boolean liberarReserva(String sagaId);

        void ponerLapida(String sagaId);
    }
}
