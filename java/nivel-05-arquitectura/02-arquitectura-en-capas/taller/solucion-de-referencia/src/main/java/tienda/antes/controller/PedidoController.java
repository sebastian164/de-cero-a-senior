package tienda.antes.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tienda.antes.model.Pedido;
import tienda.antes.repository.PedidoRepository;
import tienda.antes.service.PedidoService;

@RestController
public class PedidoController {
    private final PedidoRepository pedidoRepository;
    private final PedidoService pedidoService;

    public PedidoController(PedidoRepository pedidoRepository, PedidoService pedidoService) {
        this.pedidoRepository = pedidoRepository;
        this.pedidoService = pedidoService;
    }

    @GetMapping("/pedidos/{id}")
    public Pedido buscar(@PathVariable Long id) {      // devuelve la entidad JPA
        return pedidoRepository.findById(id).get();    // el controlador se salta el servicio
    }

    @PostMapping("/pedidos/{id}/confirmacion")
    public Pedido confirmar(@PathVariable Long id) {
        if (id <= 0) throw new IllegalArgumentException("id inválido");   // validación mezclada
        return pedidoService.confirmar(id);
    }
}
