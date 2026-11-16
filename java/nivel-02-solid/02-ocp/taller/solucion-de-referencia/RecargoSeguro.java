/** Recargo nuevo de prueba (punto 2): 1 % de seguro sobre pedidos de más de 500.000. Nada existente se modificó. */
public final class RecargoSeguro implements Recargo {
    public String nombre() { return "SEGURO"; }
    public long valor(Pedido pedido) { return pedido.base() > 500_000 ? pedido.base() / 100 : 0; }
}
