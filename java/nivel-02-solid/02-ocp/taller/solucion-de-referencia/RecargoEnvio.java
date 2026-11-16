public final class RecargoEnvio implements Recargo {
    public static final String NOMBRE = "ENVIO";
    public String nombre() { return NOMBRE; }
    public long valor(Pedido pedido) { return pedido.ciudad().equals("BOGOTA") ? 8_000 : 15_000; }
}
