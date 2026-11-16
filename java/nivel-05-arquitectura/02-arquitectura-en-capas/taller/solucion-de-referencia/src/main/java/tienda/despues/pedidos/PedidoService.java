package tienda.despues.pedidos;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

@Service
public class PedidoService {
    private final PedidoRepository pedidos;

    public PedidoService(PedidoRepository pedidos) {
        this.pedidos = pedidos;
    }

    @Transactional
    public PedidoResponse confirmar(Long id) {
        Pedido pedido = pedidos.findById(id).orElseThrow(() -> new PedidoNoEncontrado(id));
        var estadosDeuda = Arrays.stream(EstadoPedido.values()).filter(EstadoPedido::cuentaComoDeuda).toList();
        pedido.confirmar(pedidos.sumaDe(pedido.cliente().id(), estadosDeuda));
        return PedidoResponse.de(pedido);           // sin save: la entidad está gestionada
    }

    @Transactional(readOnly = true)
    public PedidoResponse buscar(Long id) {
        return pedidos.findById(id).map(PedidoResponse::de).orElseThrow(() -> new PedidoNoEncontrado(id));
    }
}
