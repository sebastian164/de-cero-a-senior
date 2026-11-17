package inventario.aplicacion;

import inventario.dominio.PedidoConfirmado;
import inventario.dominio.ResultadoDeReserva;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Puertos de salida del caso de uso. Ninguno nombra Kafka ni la base de datos. */
public final class Puertos {

    private Puertos() {
    }

    public interface EventosProcesados {
        /** Registra el evento si es nuevo (true); si ya estaba (false). Dentro de la transacción de la reserva. */
        boolean registrarSiEsNuevo(String eventoId);

        void guardarResultado(String eventoId, ResultadoDeReserva resultado);

        Optional<ResultadoDeReserva> resultadoDe(String eventoId);
    }

    public interface Existencias {
        /** Descuenta atómicamente si hay de todo. Devuelve false (sin descontar nada) si falta algo. */
        boolean reservar(String pedidoId, List<PedidoConfirmado.Linea> lineas);
    }

    public interface PublicadorDeResultados {
        void publicar(String eventoId, String pedidoId, ResultadoDeReserva resultado);
    }

    public interface Transaccion {
        <T> T ejecutar(Supplier<T> trabajo);
    }
}
