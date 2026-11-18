package acortador;

/**
 * Codificación en base 62 (la del README) y la permutación afín del README, que se conserva solo como CONTRAEJEMPLO:
 * oculta la adyacencia pero es lineal y predecible (ver Base62Test). La que usa el servicio es {@link PermutacionConClave}.
 */
public final class Base62 {
    public static final String ALFABETO = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public static final int LONGITUD = 7;
    /** 62^7 = 3.521.614.606.208 combinaciones. */
    public static final long ESPACIO = 3_521_614_606_208L;
    // Permutación afín n -> (n * A + B) mod ESPACIO. A es coprimo con 62 (impar y no múltiplo de 31),
    // así que la función es biyectiva: dos números distintos nunca dan el mismo código.
    private static final long A = 2_654_435_761L;
    private static final long B = 1_234_567_891L;
    private static final long A_INVERSO = inverso(A, ESPACIO);

    private Base62() {
    }

    public static String aBase62(long numero) {
        StringBuilder codigo = new StringBuilder();
        do {
            codigo.append(ALFABETO.charAt((int) (numero % 62)));
            numero /= 62;
        } while (numero > 0);
        return codigo.reverse().toString();
    }

    public static long desdeBase62(String codigo) {
        long n = 0;
        for (char c : codigo.toCharArray()) {
            int d = ALFABETO.indexOf(c);
            if (d < 0) throw new IllegalArgumentException("carácter fuera del alfabeto: " + c);
            n = n * 62 + d;
        }
        return n;
    }

    /** Código de 7 posiciones, con relleno a la izquierda. */
    public static String deSiete(long numero) {
        String c = aBase62(numero);
        return "0".repeat(Math.max(0, LONGITUD - c.length())) + c;
    }

    public static long permutar(long n) {
        return Math.floorMod(mulMod(n, A, ESPACIO) + B, ESPACIO);
    }

    public static final Permutacion AFIN = new Permutacion() {
        @Override public long aplicar(long n) { return permutar(n); }
        @Override public long invertir(long p) { return despermutar(p); }
    };

    public static long despermutar(long p) {
        return mulMod(Math.floorMod(p - B, ESPACIO), A_INVERSO, ESPACIO);
    }

    static long mulMod(long a, long b, long m) {
        return java.math.BigInteger.valueOf(a).multiply(java.math.BigInteger.valueOf(b))
                .mod(java.math.BigInteger.valueOf(m)).longValueExact();
    }

    private static long inverso(long a, long m) {
        return java.math.BigInteger.valueOf(a).modInverse(java.math.BigInteger.valueOf(m)).longValueExact();
    }
}
