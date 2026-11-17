package bicis;

import java.util.HashMap;
import java.util.Map;

/** Falso: sabe en qué estación quedó disponible cada bicicleta. */
final class BicicletasEnMemoria implements Bicicletas {

    private final Map<String, String> disponibles = new HashMap<>();

    @Override
    public void dejarDisponible(String bicicleta, String estacion) {
        disponibles.put(bicicleta, estacion);
    }

    boolean estaDisponibleEn(String bicicleta, String estacion) {
        return estacion.equals(disponibles.get(bicicleta));
    }
}
