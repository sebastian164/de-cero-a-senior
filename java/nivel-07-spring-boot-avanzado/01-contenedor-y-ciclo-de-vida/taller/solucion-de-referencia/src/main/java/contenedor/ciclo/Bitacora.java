package contenedor.ciclo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Registro estático de fases para poder leerlo después de cerrar el contexto. */
public final class Bitacora {
    public static final List<String> FASES = new CopyOnWriteArrayList<>();
    private Bitacora() { }
    public static void registrar(String fase) {
        FASES.add(fase);
        System.out.println("[ciclo] " + fase);
    }
}
