/** calcularCambio es propio del efectivo: no está en el contrato común. */
public final class Efectivo implements MedioDePago {

    private final long entregado;

    public Efectivo(long entregado) {
        Montos.validar(entregado);
        this.entregado = entregado;
    }

    public long calcularCambio(long monto) {
        if (monto > entregado) {
            throw new IllegalStateException("El efectivo entregado no alcanza");
        }
        return entregado - monto;
    }

    @Override
    public Comprobante pagar(long monto) {
        Montos.validar(monto);
        long cambio = calcularCambio(monto);
        return new Comprobante(monto, "Efectivo, cambio " + cambio);
    }
}
