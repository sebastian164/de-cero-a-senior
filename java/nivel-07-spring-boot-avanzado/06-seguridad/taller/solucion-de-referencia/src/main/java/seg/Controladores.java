package seg;

import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class Controladores {
    private final ConsultaPedidos consulta;

    Controladores(ConsultaPedidos consulta) { this.consulta = consulta; }

    @GetMapping("/v1/productos")
    List<String> productos() { return List.of("teclado", "monitor"); }

    @GetMapping("/v1/pedidos/{id}")
    PedidoResponse pedido(@PathVariable Long id) { return consulta.buscar(id); }

    @GetMapping("/v1/pedidos")
    List<PedidoResponse> mios(Authentication auth) { return consulta.misPedidos(auth.getName()); }

    @GetMapping("/v1/soporte/total")
    Map<String, Integer> total() { return Map.of("total", consulta.totalPedidos()); }

    @GetMapping("/v1/admin/usuarios")
    List<String> usuarios() { return List.of("ana", "luis", "marta"); }
}
