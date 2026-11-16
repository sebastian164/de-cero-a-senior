package tienda.jpa;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tienda.aplicacion.ConflictoDeConcurrencia;
import tienda.aplicacion.PedidoNoEncontrado;
import tienda.dominio.ReglaDeNegocioViolada;

@RestControllerAdvice
public class TraductorDeErrores {

    @ExceptionHandler(ReglaDeNegocioViolada.class)
    public ProblemDetail reglaViolada(ReglaDeNegocioViolada rechazo) {
        return ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(422), rechazo.getMessage());
    }

    @ExceptionHandler(PedidoNoEncontrado.class)
    public ProblemDetail noEncontrado(PedidoNoEncontrado ausente) {
        return ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(404), ausente.getMessage());
    }

    @ExceptionHandler(ConflictoDeConcurrencia.class)
    public ProblemDetail conflicto(ConflictoDeConcurrencia conflicto) {
        return ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(409), conflicto.getMessage());
    }
}
