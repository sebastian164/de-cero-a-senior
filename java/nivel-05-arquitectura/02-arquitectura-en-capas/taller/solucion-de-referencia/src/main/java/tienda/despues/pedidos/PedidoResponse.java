package tienda.despues.pedidos;

public record PedidoResponse(Long id, String cliente, long total, String estado) {
    static PedidoResponse de(Pedido p) {
        return new PedidoResponse(p.id(), p.cliente().nombre(), p.total(), p.estado().name());
    }
}
