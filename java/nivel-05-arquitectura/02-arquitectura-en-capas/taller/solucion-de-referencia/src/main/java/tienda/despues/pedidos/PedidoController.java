package tienda.despues.pedidos;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pedidos")
class PedidoController {
    private final PedidoService servicio;

    PedidoController(PedidoService servicio) { this.servicio = servicio; }

    @GetMapping("/{id}")
    PedidoResponse buscar(@PathVariable Long id) { return servicio.buscar(id); }

    @PostMapping("/{id}/confirmacion")
    PedidoResponse confirmar(@PathVariable Long id) { return servicio.confirmar(id); }

    @ExceptionHandler(CupoExcedido.class)
    ProblemDetail cupo(CupoExcedido e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }

    @ExceptionHandler(PedidoNoEncontrado.class)
    ProblemDetail noEncontrado(PedidoNoEncontrado e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
