package pedidos;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import pedidos.Errores.*;
import reactor.core.publisher.Mono;

@RestController
public class ControladorDePedidos {
    private final ServicioDePedidos servicio;
    public ControladorDePedidos(ServicioDePedidos servicio) { this.servicio = servicio; }

    /** JdbcTemplate llamado directamente en el manejador: se ejecuta en el hilo del bucle de eventos. */
    @PostMapping("/pedidos/{id}/confirmar")
    Mono<String> confirmar(@PathVariable String id, @RequestHeader(value = "Idempotency-Key", required = false) String clave) {
        return Mono.fromCallable(() -> servicio.confirmar(id, clave));
    }

    @GetMapping("/ping") Mono<String> ping() { return Mono.just("pong"); }

    @ExceptionHandler(PedidoNoEncontrado.class) ProblemDetail noEncontrado(PedidoNoEncontrado e) { return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage()); }
    @ExceptionHandler(SinExistencias.class) ProblemDetail sinExistencias(SinExistencias e) { return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage()); }
}
