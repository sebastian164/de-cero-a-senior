package banco.adaptadores.entrada.webflux;

import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.CuentaNoEncontrada;
import banco.dominio.ReglaDeNegocioViolada;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** La misma tabla de traducción que en el proyecto con JPA. Funciona igual en WebFlux. */
@RestControllerAdvice
public class TraductorDeErrores {

    @ExceptionHandler(ReglaDeNegocioViolada.class)
    public ProblemDetail reglaViolada(ReglaDeNegocioViolada rechazo) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, rechazo.getMessage());
    }

    @ExceptionHandler(CuentaNoEncontrada.class)
    public ProblemDetail noEncontrada(CuentaNoEncontrada ausente) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ausente.getMessage());
    }

    @ExceptionHandler(ConflictoDeConcurrencia.class)
    public ProblemDetail conflicto(ConflictoDeConcurrencia conflicto) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, conflicto.getMessage());
    }
}
