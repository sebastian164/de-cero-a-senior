package banco.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/** Las rutas, como datos: qué petición va a qué función, y cómo se traduce cada error. */
@Configuration
public class Rutas {

    @Bean
    public RouterFunction<ServerResponse> rutasDeCuentas(ManejadorDeCuentas manejador) {
        return RouterFunctions.route()
                .GET("/cuentas/{id}", manejador::consultar)
                .GET("/cuentas/{id}/riesgo", manejador::riesgo)
                .GET("/cuentas/{id}/extracto", manejador::extracto)
                .onError(CuentaNoEncontrada.class, (error, peticion) -> problema(HttpStatus.NOT_FOUND, error))
                .onError(RiesgoNoDisponible.class, (error, peticion) -> problema(HttpStatus.SERVICE_UNAVAILABLE, error))
                .build();
    }

    private static Mono<ServerResponse> problema(HttpStatus estado, Throwable error) {
        return ServerResponse.status(estado)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyValue(ProblemDetail.forStatusAndDetail(estado, error.getMessage()));
    }
}
