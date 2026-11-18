package pedidos;
public class PedidoNoEncontrado extends RuntimeException {
    public PedidoNoEncontrado(String id) { super("no existe el pedido " + id); }
}
