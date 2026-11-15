import java.util.Objects;

/** El resultado de construir "a números romanos" con el ciclo rojo, verde, refactor. */
public class Main {

    private static final int[] VALORES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] SIMBOLOS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    static String aRomano(int numero) {
        if (numero < 1 || numero > 3999) {
            throw new IllegalArgumentException("fuera del rango de 1 a 3999: " + numero);
        }
        StringBuilder romano = new StringBuilder();
        int resto = numero;
        for (int i = 0; i < VALORES.length; i++) {
            while (resto >= VALORES[i]) {
                romano.append(SIMBOLOS[i]);
                resto -= VALORES[i];
            }
        }
        return romano.toString();
    }

    public static void main(String[] args) {
        comprobar("1 es I", "I", aRomano(1));
        comprobar("2 es II", "II", aRomano(2));
        comprobar("3 es III", "III", aRomano(3));
        comprobar("5 es V", "V", aRomano(5));
        comprobar("6 es VI", "VI", aRomano(6));
        comprobar("4 es IV", "IV", aRomano(4));
        comprobar("10 es X", "X", aRomano(10));
        comprobar("9 es IX", "IX", aRomano(9));
        comprobar("14 es XIV", "XIV", aRomano(14));
        comprobar("40 es XL", "XL", aRomano(40));
        comprobar("90 es XC", "XC", aRomano(90));
        comprobar("400 es CD", "CD", aRomano(400));
        comprobar("1994 es MCMXCIV", "MCMXCIV", aRomano(1994));
        comprobar("3999 es MMMCMXCIX", "MMMCMXCIX", aRomano(3999));
        comprobarQueRechaza("0 no tiene representación", 0);
        comprobarQueRechaza("4000 queda fuera del rango", 4000);

        System.out.println(fallos == 0 ? "Todas pasan." : fallos + " fallan.");
        if (fallos > 0) {
            System.exit(1);
        }
    }

    // Un ejecutor de pruebas mínimo, para que el ejemplo corra sin instalar nada.
    // En el taller usarás un marco de pruebas de verdad.
    static int fallos = 0;

    static void comprobar(String nombre, Object esperado, Object obtenido) {
        if (Objects.equals(esperado, obtenido)) {
            System.out.println("  ok    " + nombre);
        } else {
            fallos++;
            System.out.println("  FALLA " + nombre + ": esperaba " + esperado + " y obtuve " + obtenido);
        }
    }

    static void comprobarQueRechaza(String nombre, int numero) {
        try {
            aRomano(numero);
            fallos++;
            System.out.println("  FALLA " + nombre + ": no lanzó error");
        } catch (IllegalArgumentException esperada) {
            System.out.println("  ok    " + nombre);
        }
    }
}
