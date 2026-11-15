import java.util.function.IntPredicate;

/** La misma cobertura, distinta capacidad de detectar un error. */
public class Main {

    static final IntPredicate ORIGINAL = edad -> edad >= 18;
    // Lo que haría una herramienta de mutación: cambiar >= por >
    static final IntPredicate MUTANTE = edad -> edad > 18;

    /** Antes: ejecutan toda la línea (cobertura del 100 %), pero lejos del límite. */
    static int pruebasDebiles(IntPredicate puedeVotar) {
        int fallos = 0;
        if (!puedeVotar.test(30)) fallos++;
        if (puedeVotar.test(5)) fallos++;
        return fallos;
    }

    /** Después: la misma cobertura, con casos justo en el límite. */
    static int pruebasFuertes(IntPredicate puedeVotar) {
        int fallos = 0;
        if (puedeVotar.test(17)) fallos++;
        if (!puedeVotar.test(18)) fallos++;
        if (!puedeVotar.test(19)) fallos++;
        return fallos;
    }

    public static void main(String[] args) {
        informar("Pruebas débiles", pruebasDebiles(ORIGINAL), pruebasDebiles(MUTANTE));
        informar("Pruebas fuertes", pruebasFuertes(ORIGINAL), pruebasFuertes(MUTANTE));
    }

    static void informar(String nombre, int fallosConOriginal, int fallosConMutante) {
        System.out.println(nombre + ":");
        System.out.println("  con el código original: " + (fallosConOriginal == 0 ? "pasan" : "fallan"));
        System.out.println("  con el mutante:         " + (fallosConMutante == 0
                ? "pasan  -> el mutante SOBREVIVE: ese error llegaría a producción"
                : "fallan -> el mutante MUERE: las pruebas lo detectan"));
    }
}
