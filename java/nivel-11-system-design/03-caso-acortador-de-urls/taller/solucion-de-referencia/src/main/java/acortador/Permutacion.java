package acortador;

/** Biyección sobre [0, Base62.ESPACIO): números distintos dan códigos distintos. */
public interface Permutacion {
    long aplicar(long n);

    long invertir(long p);
}
