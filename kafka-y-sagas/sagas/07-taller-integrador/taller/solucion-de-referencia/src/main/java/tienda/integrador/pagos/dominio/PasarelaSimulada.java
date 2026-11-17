package tienda.integrador.pagos.dominio;

/** La regla de negocio de Pagos: a la pasarela simulada no le gusta un importe. Sin ninguna tecnología. */
public final class PasarelaSimulada {

    /** Importes que la pasarela simulada rechaza. */
    public static final long IMPORTE_RECHAZADO = 666;

    private PasarelaSimulada() {
    }

    public static boolean rechaza(long centavos) {
        return centavos == IMPORTE_RECHAZADO;
    }
}
