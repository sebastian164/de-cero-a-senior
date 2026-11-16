public final class TarjetaCredito extends PagoConTarjeta {
    public TarjetaCredito(String numero) { super(numero); }
    @Override protected long comision(long monto) { return monto * 2 / 100; }
    @Override protected String tipo() { return "Crédito"; }
}
