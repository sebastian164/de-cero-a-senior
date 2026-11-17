package bicis;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Falso: un repositorio de verdad, en memoria. */
final class AlquileresEnMemoria implements Alquileres {

    private final Map<String, Alquiler> porId = new HashMap<>();

    @Override
    public Optional<Alquiler> buscar(String id) {
        return Optional.ofNullable(porId.get(id));
    }

    @Override
    public void guardar(Alquiler alquiler) {
        porId.put(alquiler.id(), alquiler);
    }
}
