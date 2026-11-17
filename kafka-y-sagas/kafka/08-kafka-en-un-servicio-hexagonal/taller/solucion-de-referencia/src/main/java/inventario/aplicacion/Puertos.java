package inventario.aplicacion;

import inventario.dominio.Existencias;
import java.util.Optional;
import java.util.function.Supplier;

/** Puertos de salida de Inventario. */
public final class Puertos {
    private Puertos() {
    }

    public interface RepositorioDeExistencias {
        Optional<Existencias> buscar(String producto);

        void guardar(Existencias existencias);

        void registrarReserva(String pedidoId, String producto, int cantidad);
    }

    /** Recuerda los eventos vistos; se escribe en la misma unidad de trabajo que la reserva. */
    public interface EventosVistos {
        boolean registrarSiEsNuevo(String eventoId);
    }

    public interface UnidadDeTrabajo {
        <T> T ejecutar(Supplier<T> trabajo);
    }
}
