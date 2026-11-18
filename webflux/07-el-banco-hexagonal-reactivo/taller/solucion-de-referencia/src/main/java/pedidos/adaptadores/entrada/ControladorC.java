package pedidos.adaptadores.entrada;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import pedidos.aplicacion.reactiva.ServicioDePedidos;
import reactor.core.publisher.Mono;

/** Opción C: puertos reactivos, adaptadores R2DBC. */
@RestController
@ConditionalOnProperty(name = "opcion", havingValue = "c")
public class ControladorC {
    private final ServicioDePedidos servicio;
    public ControladorC(ServicioDePedidos servicio) { this.servicio = servicio; }

    @PostMapping("/pedidos/{id}/confirmar") Mono<Resumen> confirmar(@PathVariable String id) { return servicio.confirmar(id).map(Resumen::de); }
    @GetMapping("/pedidos/{id}") Mono<Resumen> consultar(@PathVariable String id) { return servicio.consultar(id).map(Resumen::de); }
    @GetMapping("/ping") Mono<String> ping() { return Mono.just("pong"); }
}
