package pedidos.adaptadores.entrada;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import pedidos.aplicacion.sincrona.ServicioDePedidos;
import reactor.core.publisher.Mono;

/** Opción A: WebFlux sobre puertos síncronos. El puerto bloquea el hilo del bucle de eventos. */
@RestController
@ConditionalOnProperty(name = "opcion", havingValue = "a")
public class ControladorA {
    private final ServicioDePedidos servicio;
    public ControladorA(ServicioDePedidos servicio) { this.servicio = servicio; }

    @PostMapping("/pedidos/{id}/confirmar") Mono<Resumen> confirmar(@PathVariable String id) { return Mono.fromCallable(() -> Resumen.de(servicio.confirmar(id))); }
    @GetMapping("/pedidos/{id}") Mono<Resumen> consultar(@PathVariable String id) { return Mono.fromCallable(() -> Resumen.de(servicio.consultar(id))); }
    @GetMapping("/ping") Mono<String> ping() { return Mono.just("pong"); }
}
