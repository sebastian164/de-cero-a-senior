package pedidos.adaptadores.entrada;

import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.reactive.function.server.*;
import pedidos.aplicacion.Errores.*;
import pedidos.aplicacion.ServicioDePedidos;
import reactor.core.publisher.Mono;
import tienda.aplicacion.ConflictoDeConcurrencia;
import tienda.aplicacion.PedidoNoEncontrado;
import tienda.dominio.Pedido;
import tienda.dominio.ReglaDeNegocioViolada;

public final class Rutas {
    private Rutas() {}

    public record Resumen(String id, String cliente, String estado, int lineas, long totalCentavos) {
        static Resumen de(Pedido p) { return new Resumen(p.id(), p.cliente(), p.estado().name(), p.lineas().size(), p.total().centavos()); }
    }

    public static RouterFunction<ServerResponse> rutas(ServicioDePedidos servicio) {
        return RouterFunctions.route()
                .POST("/pedidos/{id}/confirmacion", r -> servicio.confirmar(r.pathVariable("id")).flatMap(p -> ServerResponse.ok().bodyValue(Resumen.de(p))))
                .GET("/pedidos/{id}", r -> servicio.consultar(r.pathVariable("id")).flatMap(p -> ServerResponse.ok().bodyValue(Resumen.de(p))))
                .GET("/pedidos/{id}/eventos", r -> ServerResponse.ok().contentType(MediaType.TEXT_EVENT_STREAM)
                        .body(servicio.eventos(r.pathVariable("id")).take(Duration.ofSeconds(30)), String.class))
                .GET("/ping", r -> ServerResponse.ok().bodyValue("pong"))
                .onError(PedidoNoEncontrado.class, (e, r) -> problema(HttpStatus.NOT_FOUND, e))
                .onError(SinExistenciasEnInventario.class, (e, r) -> problema(HttpStatus.CONFLICT, e))
                .onError(ConflictoDeConcurrencia.class, (e, r) -> problema(HttpStatus.CONFLICT, e))
                .onError(PagoRechazado.class, (e, r) -> problema(HttpStatus.UNPROCESSABLE_ENTITY, e))
                .onError(ServicioExternoNoDisponible.class, (e, r) -> problema(HttpStatus.SERVICE_UNAVAILABLE, e))
                .onError(ReglaDeNegocioViolada.class, (e, r) -> problema(HttpStatus.UNPROCESSABLE_ENTITY, e))
                .build();
    }

    static Mono<ServerResponse> problema(HttpStatus estado, Throwable e) {
        return ServerResponse.status(estado).contentType(MediaType.APPLICATION_PROBLEM_JSON).bodyValue(ProblemDetail.forStatusAndDetail(estado, e.getMessage()));
    }
}
