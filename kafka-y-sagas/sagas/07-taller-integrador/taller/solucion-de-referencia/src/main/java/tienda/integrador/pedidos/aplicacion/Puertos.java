package tienda.integrador.pedidos.aplicacion;

import java.util.Optional;
import java.util.function.Supplier;
import tienda.integrador.pedidos.dominio.Pedido;

/** Puertos de Pedidos. Ninguno menciona Kafka, SQL, HTTP ni Spring. */
public final class Puertos {

    private Puertos() {
    }

    // ---- entrada ----

    public interface AceptarPedido {
        record Solicitud(String id, String correlacion, String producto, int cantidad, long centavos, String ciudad) {
        }

        /** Idempotente por id: repetir la solicitud no crea otro pedido ni otro evento. */
        Pedido aceptar(Solicitud solicitud);
    }

    public interface ConsultarPedido {
        Optional<Pedido> porId(String id);

        long pendientes();
    }

    public interface RegistrarResultadoDeSaga {
        /** El id del evento es estable: el mismo resultado dos veces cambia el pedido una sola vez. */
        record Resultado(String eventoId, String pedidoId, String correlacion, boolean completada, String motivo) {
        }

        void registrar(Resultado resultado);
    }

    // ---- salida ----

    public interface RepositorioDePedidos {
        /** false si ya existía (la solicitud era una repetición). */
        boolean crear(Pedido pedido);

        Optional<Pedido> buscar(String id);

        void cerrar(String id, Pedido.Estado estado, String motivo);

        long contarPendientes();
    }

    public interface MensajesProcesados {
        boolean marcarNuevo(String id);
    }

    /** La bandeja de salida: el evento se escribe en la misma unidad de trabajo que el pedido. */
    public interface BandejaDeSalida {
        void anunciarPedidoCreado(Pedido pedido);
    }

    public interface UnidadDeTrabajo {
        <T> T ejecutar(Supplier<T> trabajo);
    }
}
