package pedidos;
public record Pedido(String id, long monto, String estado) {
    public Pedido con(String estado) { return new Pedido(id, monto, estado); }
}
