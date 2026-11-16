package tienda.aplicacion;

public class PedidoNoEncontrado extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PedidoNoEncontrado(String pedidoId) {
        super("no existe el pedido " + pedidoId);
    }
}
