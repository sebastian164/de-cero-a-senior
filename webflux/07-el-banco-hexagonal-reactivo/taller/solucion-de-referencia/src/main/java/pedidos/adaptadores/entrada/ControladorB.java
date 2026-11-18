package pedidos.adaptadores.entrada;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import pedidos.aplicacion.sincrona.ServicioDePedidos;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Opción B: el mismo núcleo síncrono, cada llamada desviada a boundedElastic. */
@RestController
@ConditionalOnProperty(name = "opcion", havingValue = "b")
public class ControladorB {
    private final ServicioDePedidos servicio;
    public ControladorB(ServicioDePedidos servicio) { this.servicio = servicio; }

    @PostMapping("/pedidos/{id}/confirmar") Mono<Resumen> confirmar(@PathVariable String id) {
        return Mono.fromCallable(() -> Resumen.de(servicio.confirmar(id))).subscribeOn(Schedulers.boundedElastic());
    }
    @GetMapping("/pedidos/{id}") Mono<Resumen> consultar(@PathVariable String id) {
        return Mono.fromCallable(() -> Resumen.de(servicio.consultar(id))).subscribeOn(Schedulers.boundedElastic());
    }
    @GetMapping("/ping") Mono<String> ping() { return Mono.just("pong"); }
}
