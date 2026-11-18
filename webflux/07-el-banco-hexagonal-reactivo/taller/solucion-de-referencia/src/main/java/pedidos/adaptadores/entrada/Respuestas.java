package pedidos.adaptadores.entrada;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tienda.aplicacion.ConflictoDeConcurrencia;
import tienda.aplicacion.PedidoNoEncontrado;
import tienda.dominio.ReglaDeNegocioViolada;

@RestControllerAdvice
public class Respuestas {
    @ExceptionHandler(PedidoNoEncontrado.class) ProblemDetail a(PedidoNoEncontrado e) { return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage()); }
    @ExceptionHandler(ConflictoDeConcurrencia.class) ProblemDetail b(ConflictoDeConcurrencia e) { return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage()); }
    @ExceptionHandler(ReglaDeNegocioViolada.class) ProblemDetail c(ReglaDeNegocioViolada e) { return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage()); }
}
