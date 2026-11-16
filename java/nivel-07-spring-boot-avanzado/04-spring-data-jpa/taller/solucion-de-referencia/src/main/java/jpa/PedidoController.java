package jpa;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
class PedidoController {

    private final PedidoRepository pedidos;
    private final ConsultaPedidos consulta;

    PedidoController(PedidoRepository pedidos, ConsultaPedidos consulta) {
        this.pedidos = pedidos;
        this.consulta = consulta;
    }

    /** Lo que se rompe al apagar open-in-view: la asociación perezosa se toca en la capa web. */
    @GetMapping("/antes/pedidos/{id}")
    Map<String, Object> antes(@PathVariable Long id) {
        Pedido p = pedidos.findById(id).orElseThrow();
        return Map.of("id", p.getId(), "cliente", p.getCliente().getNombre());
    }

    /** Arreglado: el caso de uso arma la respuesta dentro de su transacción. */
    @GetMapping("/pedidos/{id}")
    Map<String, Object> despues(@PathVariable Long id) {
        return consulta.detalle(id);
    }
}
