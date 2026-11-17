import java.util.ArrayList;
import java.util.List;

/**
 * Lo que protege el cerrojo. Ahora recuerda el mayor turno que ha visto y rechaza a quien llega con uno menor:
 * eso es una ficha de cercado (fencing token). En un sistema real el libro es una base de datos y la comprobación
 * va en la escritura, como el filtro condicional de la sesión 03: updateOne({_id, turno: {$lt: t}}, ...).
 */
final class LibroMayor {

    private final List<String> asientos = new ArrayList<>();
    private long mayorTurno = 0;

    synchronized boolean escribir(long turno, String quien, String asiento) {
        if (turno < mayorTurno) {
            return false;
        }
        mayorTurno = turno;
        asientos.add(quien + " (turno " + turno + "): " + asiento);
        return true;
    }

    synchronized List<String> asientos() {
        return List.copyOf(asientos);
    }
}
