package citas;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

/** Errores uniformes con Problem Details (RFC 9457). Nunca se devuelve la traza ni nombres internos. */
@RestControllerAdvice
class ManejadorDeErrores extends ResponseEntityExceptionHandler {

    private static final String BASE = "https://api.citas.example/errores/";

    @ExceptionHandler(ErrorDeApi.NoEncontrada.class)
    ProblemDetail noEncontrada(ErrorDeApi e, HttpServletRequest peticion) {
        return problema(HttpStatus.NOT_FOUND, "no-encontrada", "Cita no encontrada", e.getMessage(), peticion);
    }

    @ExceptionHandler(ErrorDeApi.Conflicto.class)
    ProblemDetail conflicto(ErrorDeApi e, HttpServletRequest peticion) {
        return problema(HttpStatus.CONFLICT, "conflicto", "Conflicto de estado", e.getMessage(), peticion);
    }

    /** Dos peticiones simultáneas con la misma Idempotency-Key: la segunda pierde en la clave primaria y debe reintentar. */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ProblemDetail claveEnUso(HttpServletRequest peticion) {
        return problema(HttpStatus.CONFLICT, "peticion-en-curso", "Petición en curso",
                "Otra petición con la misma Idempotency-Key se está procesando; reintenta", peticion);
    }

    @ExceptionHandler(ErrorDeApi.ReglaDeNegocio.class)
    ProblemDetail regla(ErrorDeApi.ReglaDeNegocio e, HttpServletRequest peticion) {
        return problema(HttpStatus.UNPROCESSABLE_CONTENT, e.tipo, e.titulo, e.getMessage(), peticion);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        ProblemDetail p = ex.getBody();
        p.setType(URI.create(BASE + "validacion"));
        p.setTitle("Datos inválidos");
        p.setDetail("La petición tiene campos con formato inválido");
        Map<String, String> errores = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(f -> f.getField(), f -> String.valueOf(f.getDefaultMessage()), (a, b) -> a));
        p.setProperty("errores", errores);
        return handleExceptionInternal(ex, p, headers, status, request);
    }

    private static ProblemDetail problema(HttpStatus estado, String tipo, String titulo, String detalle,
                                          HttpServletRequest peticion) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(estado, detalle);
        p.setType(URI.create(BASE + tipo));
        p.setTitle(titulo);
        p.setInstance(URI.create(peticion.getRequestURI()));
        return p;
    }
}
