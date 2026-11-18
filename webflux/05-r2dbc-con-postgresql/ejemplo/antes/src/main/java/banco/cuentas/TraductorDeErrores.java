package banco.cuentas;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** @RestControllerAdvice funciona igual en WebFlux que en MVC. */
@RestControllerAdvice
public class TraductorDeErrores {

    @ExceptionHandler(CuentaNoEncontrada.class)
    public ProblemDetail noEncontrada(CuentaNoEncontrada error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.getMessage());
    }

    @ExceptionHandler(SaldoInsuficiente.class)
    public ProblemDetail sinSaldo(SaldoInsuficiente error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, error.getMessage());
    }
}
