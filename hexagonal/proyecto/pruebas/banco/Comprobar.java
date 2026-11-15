package banco;

import java.util.Objects;

/** Un comprobador mínimo para no depender de nada. En un proyecto real, esto es JUnit. */
public final class Comprobar {

    private static int total = 0;
    private static int fallos = 0;

    private Comprobar() {
    }

    public static void igual(String nombre, Object esperado, Object obtenido) {
        total++;
        if (Objects.equals(esperado, obtenido)) {
            System.out.println("  ok    " + nombre);
        } else {
            fallos++;
            System.out.println("  FALLA " + nombre + ": esperaba " + esperado + " y obtuve " + obtenido);
        }
    }

    public static void lanza(String nombre, Class<? extends RuntimeException> tipo, Runnable accion) {
        total++;
        try {
            accion.run();
            fallos++;
            System.out.println("  FALLA " + nombre + ": no lanzó " + tipo.getSimpleName());
        } catch (RuntimeException error) {
            if (tipo.isInstance(error)) {
                System.out.println("  ok    " + nombre);
            } else {
                fallos++;
                System.out.println("  FALLA " + nombre + ": lanzó " + error);
            }
        }
    }

    public static int total() {
        return total;
    }

    public static int fallos() {
        return fallos;
    }
}
