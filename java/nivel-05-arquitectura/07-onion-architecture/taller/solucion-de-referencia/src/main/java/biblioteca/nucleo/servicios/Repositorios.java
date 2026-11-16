package biblioteca.nucleo.servicios;

import biblioteca.nucleo.modelo.Ejemplar;
import biblioteca.nucleo.modelo.Prestamo;
import biblioteca.nucleo.modelo.Socio;

import java.util.Optional;

/** En Onion, las interfaces de repositorio viven en el anillo de servicios de dominio. */
public final class Repositorios {
    private Repositorios() { }

    public interface Socios { Optional<Socio> buscar(String id); void guardar(Socio socio); }
    public interface Ejemplares { Optional<Ejemplar> buscar(String codigo); void guardar(Ejemplar ejemplar); }
    public interface Prestamos {
        void guardar(Prestamo prestamo);
        int activosDe(String socioId);
        Optional<Prestamo> activoDelEjemplar(String codigo);
    }
}
