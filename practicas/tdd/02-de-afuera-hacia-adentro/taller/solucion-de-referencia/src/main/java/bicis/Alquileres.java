package bicis;

import java.util.Optional;

/** Puerto: dónde viven los alquileres. Apareció porque la prueba necesita un alquiler iniciado. */
public interface Alquileres {

    Optional<Alquiler> buscar(String id);

    void guardar(Alquiler alquiler);
}
