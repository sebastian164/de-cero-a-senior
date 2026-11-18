package pedidos;
public record Pedido(String id, long monto, String estado) {
    public Pedido confirmado() { return new Pedido(id, monto, "CONFIRMADO"); }
}
