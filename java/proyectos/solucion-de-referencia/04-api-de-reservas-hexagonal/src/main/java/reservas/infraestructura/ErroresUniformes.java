package reservas.infraestructura;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import reservas.dominio.Reserva;

/** Todos los errores salen como application/problem+json (RFC 9457). */
@RestControllerAdvice
public class ErroresUniformes {
    @ExceptionHandler(Reserva.FranjaOcupada.class)
    ProblemDetail ocupada(Reserva.FranjaOcupada e) { return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage()); }

    @ExceptionHandler(Reserva.CancelacionFueraDePlazo.class)
    ProblemDetail plazo(Reserva.CancelacionFueraDePlazo e) { return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage()); }

    @ExceptionHandler(Reserva.NoEncontrada.class)
    ProblemDetail noEncontrada(Reserva.NoEncontrada e) { return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage()); }

    @ExceptionHandler(Reserva.SinPermiso.class)
    ProblemDetail prohibido(Reserva.SinPermiso e) { return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage()); }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ProblemDetail invalida(Exception e) { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Petición inválida: " + e.getMessage()); }
}
