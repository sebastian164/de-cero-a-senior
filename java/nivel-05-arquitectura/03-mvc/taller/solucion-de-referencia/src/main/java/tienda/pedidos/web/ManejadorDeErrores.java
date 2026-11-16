package tienda.pedidos.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tienda.pedidos.aplicacion.PedidoNoEncontrado;
import tienda.pedidos.dominio.CancelacionRechazada;

@RestControllerAdvice
class ManejadorDeErrores {

    @ExceptionHandler(CancelacionRechazada.class)
    ProblemDetail rechazada(CancelacionRechazada e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(PedidoNoEncontrado.class)
    ProblemDetail noEncontrado(PedidoNoEncontrado e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
