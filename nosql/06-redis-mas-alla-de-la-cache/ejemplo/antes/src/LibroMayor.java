import java.util.ArrayList;
import java.util.List;

/** Lo que protege el cerrojo: el cierre del día. Si dos lo escriben a la vez, queda mal. Acepta cualquier escritura. */
final class LibroMayor {

    private final List<String> asientos = new ArrayList<>();

    synchronized boolean escribir(String quien, String asiento) {
        asientos.add(quien + ": " + asiento);
        return true;
    }

    synchronized List<String> asientos() {
        return List.copyOf(asientos);
    }
}
