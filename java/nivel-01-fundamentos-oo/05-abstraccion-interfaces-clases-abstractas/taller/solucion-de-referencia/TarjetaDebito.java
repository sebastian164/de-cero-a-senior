public final class TarjetaDebito extends PagoConTarjeta {
    public TarjetaDebito(String numero) { super(numero); }
    @Override protected long comision(long monto) { return 0; }
    @Override protected String tipo() { return "Débito"; }
}
