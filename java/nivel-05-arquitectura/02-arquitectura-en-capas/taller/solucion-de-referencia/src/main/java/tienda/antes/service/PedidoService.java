package tienda.antes.service;

import org.springframework.stereotype.Service;
import tienda.antes.model.Pedido;
import tienda.antes.repository.PedidoRepository;

@Service
public class PedidoService {
    private final PedidoRepository pedidoRepository;

    public PedidoService(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }

    /** La regla del cupo vive en el servicio, mezclada con la carga y el guardado. */
    public Pedido confirmar(Long id) {
        Pedido pedido = pedidoRepository.findById(id).orElseThrow();
        if (!pedido.getEstado().equals("BORRADOR")) throw new IllegalStateException("No está en borrador");
        long deuda = pedidoRepository.deudaDe(pedido.getCliente().getId());
        if (deuda + pedido.getTotal() > pedido.getCliente().getCupo()) {
            throw new IllegalStateException("Cupo excedido");
        }
        pedido.setEstado("CONFIRMADO");
        return pedidoRepository.save(pedido);
    }

    public Pedido buscar(Long id) {                 // capa de paso: solo reenvía
        return pedidoRepository.findById(id).orElseThrow();
    }
}
