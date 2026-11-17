package bicis;

/** Puerto: el registro del pago. Apareció porque "se cobran dos bloques" es un efecto hacia fuera. */
public interface Cobros {

    void registrar(String socio, int valor);
}
