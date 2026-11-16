/**
 * Eje que se dejó SIN abstraer a propósito: las tarifas de impuesto las fija la ley,
 * cambian poco y en bloque. Un enum cerrado es más claro que una clase por tarifa.
 */
public enum Categoria {
    TECNOLOGIA(19), ALIMENTOS(5), OTRA(0);

    private final int porcentajeImpuesto;

    Categoria(int porcentajeImpuesto) { this.porcentajeImpuesto = porcentajeImpuesto; }

    long impuestoSobre(long base) { return base * porcentajeImpuesto / 100; }
}
