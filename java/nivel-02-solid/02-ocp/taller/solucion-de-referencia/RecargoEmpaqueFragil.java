public final class RecargoEmpaqueFragil implements Recargo {
    public String nombre() { return "EMPAQUE"; }
    public long valor(Pedido pedido) { return pedido.fragil() ? 6_000 : 0; }
}
