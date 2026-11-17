package tienda.aplicacion.saga;

import java.util.List;
import java.util.Optional;
import tienda.dominio.saga.SagaDeCompra;

/**
 * Los puertos nuevos de la saga, agrupados por brevedad. Del servicio de pedidos se reutilizan, sin tocarlos, los puertos de entrada
 * (CrearPedido, ConfirmarPedido, CancelarPedido, ConsultarPedido) y la UnidadDeTrabajo de salida.
 */
public final class Puertos {

    private Puertos() {
    }

    // ---- entrada ----

    public interface ConfirmarCompra {
        /** La clave es la de idempotencia del pedido: repetir la compra con la misma clave no crea otra. */
        record Compra(String clave, String cliente, String producto, int unidades, String ciudad) {
        }

        SagaDeCompra.Estado confirmar(Compra compra);
    }

    public interface ReanudarSagas {
        /** Lleva adelante cada saga abierta tanto como se pueda. Devuelve cuántas quedaron cerradas. */
        int reanudar();
    }

    // ---- salida: un puerto por participante; todos reciben el id de la saga ----

    public interface Inventario {
        enum Respuesta { RESERVADO, SIN_EXISTENCIAS }

        Respuesta reservar(String sagaId, String producto, int unidades);

        void liberar(String sagaId);
    }

    public interface Pagos {
        enum Respuesta { APROBADO, RECHAZADO }

        Respuesta cobrar(String sagaId, long centavos);

        void reembolsar(String sagaId);
    }

    public interface Envios {
        enum Respuesta { PROGRAMADO, IMPOSIBLE }

        Respuesta programar(String sagaId, String ciudad);
    }

    /** El participante no respondió. No dice si hizo el trabajo o no: no se sabe. */
    public static final class SinRespuesta extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public SinRespuesta(String detalle) {
            super(detalle);
        }
    }

    // ---- salida: persistencia de las sagas (comparte la unidad de trabajo del servicio de pedidos) ----

    public interface RepositorioDeSagas {
        Optional<SagaDeCompra> buscar(String id);

        void guardar(SagaDeCompra saga);

        List<SagaDeCompra> abiertas();
    }
}
