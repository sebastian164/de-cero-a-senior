package tienda;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Copiado tal cual de la lección 07 ("Aplicación profesional"); solo se agregaron package e imports.
@RestControllerAdvice
class ManejadorDeErrores {

    @ExceptionHandler(RecursoNoEncontradoException.class)
    ResponseEntity<ProblemDetail> noEncontrado(RecursoNoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage()));
    }

    @ExceptionHandler(ReglaDeNegocioException.class)
    ResponseEntity<ProblemDetail> regla(ReglaDeNegocioException e) {
        return ResponseEntity.unprocessableEntity()
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage()));
    }
}
