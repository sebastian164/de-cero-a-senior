package patrones.saga;

/** Una transacción local con su compensación. Ambas deben ser idempotentes. */
public interface Paso<C> {
    String nombre();
    void ejecutar(C contexto);
    void compensar(C contexto);
}
