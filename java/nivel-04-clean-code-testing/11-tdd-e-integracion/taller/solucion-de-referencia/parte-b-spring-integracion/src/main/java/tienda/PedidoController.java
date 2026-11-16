package tienda;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pedidos")
class PedidoController {

    record PedidoRespuesta(Long id, String cliente, EstadoPedido estado, long total) {
        static PedidoRespuesta de(Pedido p) {
            return new PedidoRespuesta(p.getId(), p.getCliente(), p.getEstado(), p.getTotal());
        }
    }

    private final ServicioPedidos servicio;

    PedidoController(ServicioPedidos servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PedidoRespuesta crear(@Valid @RequestBody SolicitudPedido solicitud) {
        return PedidoRespuesta.de(servicio.crear(solicitud));
    }

    @GetMapping("/{id}")
    PedidoRespuesta buscar(@PathVariable long id) {
        return PedidoRespuesta.de(servicio.buscar(id));
    }

    @PostMapping("/{id}/entrega")
    PedidoRespuesta entregar(@PathVariable long id) {
        return PedidoRespuesta.de(servicio.entregar(id));
    }
}
