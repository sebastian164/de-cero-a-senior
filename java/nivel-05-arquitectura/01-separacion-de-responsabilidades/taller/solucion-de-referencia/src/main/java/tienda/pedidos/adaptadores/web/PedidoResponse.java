package tienda.pedidos.adaptadores.web;

import tienda.pedidos.dominio.Pedido;

record PedidoResponse(Long id, long total, String mensaje) {
    static PedidoResponse de(Pedido pedido) {
        return new PedidoResponse(pedido.id(), pedido.total(), "Creado");
    }
}
