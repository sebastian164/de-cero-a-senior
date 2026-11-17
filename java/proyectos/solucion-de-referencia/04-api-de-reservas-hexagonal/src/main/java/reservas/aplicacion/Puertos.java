package reservas.aplicacion;

import reservas.dominio.Franja;
import reservas.dominio.Reserva;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Puertos de salida: lo que los casos de uso necesitan del mundo exterior. */
public final class Puertos {
    private Puertos() {}

    public record Pagina<T>(List<T> contenido, int pagina, int tamano, long total) {}
    public record Espera(String espacio, String usuario, Franja franja, long id) {}

    public interface RepositorioReservas {
        /** @throws Reserva.FranjaOcupada si se solapa con otra reserva activa del mismo espacio */
        void guardar(Reserva r);
        void actualizar(Reserva r);
        Optional<Reserva> buscar(UUID id);
        /** Franjas de reservas activas del espacio que tocan la ventana [desde, hasta). */
        List<Franja> ocupadas(String espacio, java.time.Instant desde, java.time.Instant hasta);
        Pagina<Reserva> listar(String usuarioOnull, int pagina, int tamano);
        Optional<Reserva> porClave(String clave, String usuario);
        void asociarClave(String clave, String usuario, UUID reserva);
        void esperar(String espacio, String usuario, Franja franja);
        Optional<Espera> primeroEnEspera(String espacio, Franja franja);
        void quitarDeEspera(long id);
    }

    public interface Transaccion {
        <T> T ejecutar(Supplier<T> trabajo);
    }
}
