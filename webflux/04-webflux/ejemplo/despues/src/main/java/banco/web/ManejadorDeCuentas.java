package banco.web;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/** Las funciones que atienden cada ruta. Reciben la petición y devuelven la respuesta "por llegar". */
@Component
public class ManejadorDeCuentas {

    private final AlmacenDeCuentas almacen;
    private final ClienteDeRiesgo riesgo;

    public ManejadorDeCuentas(AlmacenDeCuentas almacen, ClienteDeRiesgo riesgo) {
        this.almacen = almacen;
        this.riesgo = riesgo;
    }

    public Mono<ServerResponse> consultar(ServerRequest peticion) {
        return exigir(peticion.pathVariable("id"))
                .flatMap(cuenta -> ServerResponse.ok().bodyValue(cuenta));
    }

    public Mono<ServerResponse> riesgo(ServerRequest peticion) {
        return exigir(peticion.pathVariable("id"))
                .flatMap(cuenta -> riesgo.evaluar(cuenta.id()))   // el hilo queda libre mientras el otro responde
                .flatMap(evaluacion -> ServerResponse.ok().bodyValue(evaluacion));
    }

    /** Server-Sent Events: cada renglón sale hacia el cliente en cuanto existe. */
    public Mono<ServerResponse> extracto(ServerRequest peticion) {
        String id = peticion.pathVariable("id");
        return exigir(id).flatMap(cuenta -> ServerResponse.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(almacen.extracto(id), Movimiento.class));
    }

    private Mono<Cuenta> exigir(String id) {
        return almacen.buscar(id).switchIfEmpty(Mono.error(new CuentaNoEncontrada(id)));
    }
}
