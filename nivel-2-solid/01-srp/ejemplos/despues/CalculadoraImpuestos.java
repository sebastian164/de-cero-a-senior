/**
 * Razón para cambiar: la norma tributaria.
 * Quién pide el cambio: contabilidad.
 */
public class CalculadoraImpuestos {

    private static final long PORCENTAJE_IVA = 19;

    public long iva(Factura factura) {
        return factura.subtotal() * PORCENTAJE_IVA / 100;
    }

    public long total(Factura factura) {
        return factura.subtotal() + iva(factura);
    }
}
