package tienda.pedidos.dominio;

/** Después: solo importa tipos del negocio. Spring lo ensambla desde afuera. */
public class ConfirmarPedido {
    private final RepositorioPedidos pedidos;
    private final PasarelaPagos pagos;

    public ConfirmarPedido(RepositorioPedidos pedidos, PasarelaPagos pagos) {
        this.pedidos = pedidos;
        this.pagos = pagos;
    }

    public Pedido ejecutar(long pedidoId, MedioDePago medio) {
        Pedido pedido = pedidos.buscar(pedidoId).orElseThrow(() -> new IllegalArgumentException("no existe " + pedidoId));
        pedido.registrarPago(pagos.cobrar(pedido.total(), medio));
        pedidos.guardar(pedido);
        return pedido;
    }
}
