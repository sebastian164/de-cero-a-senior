package pedidos;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Configuration
public class Rutas {

    @Bean
    RouterFunction<ServerResponse> rutasDePedidos(ConfirmarPedido confirmar, AlmacenDePedidos almacen, EventosDePedido eventos) {
        return RouterFunctions.route()
                .POST("/pedidos/{id}/confirmacion", r -> confirmar.confirmar(r.pathVariable("id")).flatMap(p -> ServerResponse.ok().bodyValue(p)))
                .GET("/pedidos/{id}", r -> almacen.buscar(r.pathVariable("id"))
                        .switchIfEmpty(Mono.error(() -> new PedidoNoEncontrado(r.pathVariable("id"))))
                        .flatMap(p -> ServerResponse.ok().bodyValue(p)))
                .GET("/pedidos/{id}/eventos", r -> {
                    String id = r.pathVariable("id");
                    return almacen.buscar(id).switchIfEmpty(Mono.error(() -> new PedidoNoEncontrado(id)))
                            .flatMap(p -> {
                                // un pedido que ya terminó responde de inmediato su estado final, sin esperar 30 s a eventos que no llegarán
                                Flux<String> estados = p.estado().equals("CONFIRMADO") ? Flux.just("CONFIRMADO") : eventos.flujo(id);
                                Flux<ServerSentEvent<String>> flujo = estados.map(e -> ServerSentEvent.builder(e).event("estado").build())
                                        .take(Duration.ofSeconds(30));
                                return ServerResponse.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(flujo, ServerSentEvent.class);
                            });
                })
                .onError(PedidoNoEncontrado.class, (e, r) -> problema(HttpStatus.NOT_FOUND, e))
                .onError(SinExistencias.class, (e, r) -> problema(HttpStatus.CONFLICT, e))
                .onError(PagoRechazado.class, (e, r) -> problema(HttpStatus.UNPROCESSABLE_ENTITY, e))
                .onError(ServicioExternoNoDisponible.class, (e, r) -> problema(HttpStatus.SERVICE_UNAVAILABLE, e))
                .build();
    }

    static Mono<ServerResponse> problema(HttpStatus estado, Throwable e) {
        return ServerResponse.status(estado).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyValue(ProblemDetail.forStatusAndDetail(estado, e.getMessage()));
    }
}
