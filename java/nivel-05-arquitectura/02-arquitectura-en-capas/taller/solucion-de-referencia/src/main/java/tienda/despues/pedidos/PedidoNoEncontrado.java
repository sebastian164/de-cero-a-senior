package tienda.despues.pedidos;

public class PedidoNoEncontrado extends RuntimeException {
    public PedidoNoEncontrado(Long id) { super("Pedido " + id + " no existe"); }
}
