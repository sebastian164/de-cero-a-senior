package jpa;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/** Taller 2: con open-in-view apagado, la lectura de asociaciones perezosas se hace dentro de una transacción. */
@Service
public class ConsultaPedidos {

    private final PedidoRepository pedidos;

    ConsultaPedidos(PedidoRepository pedidos) {
        this.pedidos = pedidos;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detalle(Long id) {
        Pedido p = pedidos.findConDetalleById(id).orElseThrow();
        return Map.of("id", p.getId(), "cliente", p.getCliente().getNombre(), "lineas", p.getLineas().size());
    }
}
