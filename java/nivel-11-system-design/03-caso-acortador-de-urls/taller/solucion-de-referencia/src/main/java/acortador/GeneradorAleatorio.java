package acortador;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

/** Aleatorio con comprobación: impredecible, pero la unicidad la verifica la base (clave primaria). */
public class GeneradorAleatorio implements GeneradorDeCodigos {
    private final RandomGenerator azar;
    private final int longitud;

    public GeneradorAleatorio() {
        this(new SecureRandom(), Base62.LONGITUD);
    }

    public GeneradorAleatorio(RandomGenerator azar, int longitud) {
        this.azar = azar;
        this.longitud = longitud;
    }

    @Override
    public String siguiente() {
        char[] c = new char[longitud];
        for (int i = 0; i < longitud; i++) {
            c[i] = Base62.ALFABETO.charAt(azar.nextInt(62));
        }
        return new String(c);
    }

    @Override
    public String nombre() {
        return "aleatorio";
    }
}
