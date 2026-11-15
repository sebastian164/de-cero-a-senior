package banco.jpa;

import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.CuentaNoEncontrada;
import banco.dominio.ReglaDeNegocioViolada;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** La tabla de traducción de la sesión 06, ahora en un solo sitio. */
@RestControllerAdvice
public class TraductorDeErrores {

    @ExceptionHandler(ReglaDeNegocioViolada.class)
    public ProblemDetail reglaViolada(ReglaDeNegocioViolada rechazo) {
        return ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(422), rechazo.getMessage());
    }

    @ExceptionHandler(CuentaNoEncontrada.class)
    public ProblemDetail noEncontrada(CuentaNoEncontrada ausente) {
        return ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(404), ausente.getMessage());
    }

    @ExceptionHandler(ConflictoDeConcurrencia.class)
    public ProblemDetail conflicto(ConflictoDeConcurrencia conflicto) {
        return ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(409), conflicto.getMessage());
    }
}
