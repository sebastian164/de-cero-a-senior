package tienda.pedidos.aplicacion;

public class PedidoNoEncontrado extends RuntimeException {
    public PedidoNoEncontrado(long id) { super("Pedido " + id + " no existe"); }
}
