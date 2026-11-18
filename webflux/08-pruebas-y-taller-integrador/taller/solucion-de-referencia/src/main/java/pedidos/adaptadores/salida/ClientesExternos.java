package pedidos.adaptadores.salida;

import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import pedidos.aplicacion.Errores.*;
import pedidos.aplicacion.puertos.*;
import reactor.core.publisher.Mono;
import tienda.dominio.Pedido;

public final class ClientesExternos {
    private ClientesExternos() {}

    /** Inventario: 1 s (responde en ~300 ms). La clave de idempotencia es el id del pedido, también en la ruta. */
    public static class ClienteDeInventario implements Inventario {
        private final WebClient cliente;
        public ClienteDeInventario(WebClient.Builder b, String url) { this.cliente = b.baseUrl(url).build(); }
        public Mono<Void> reservar(Pedido p) {
            return cliente.post().uri("/reservar/{id}", p.id()).header("Idempotency-Key", p.id()).retrieve()
                    .onStatus(s -> s.value() == HttpStatus.CONFLICT.value(), r -> Mono.error(new SinExistenciasEnInventario(p.id())))
                    .bodyToMono(Void.class).timeout(Duration.ofSeconds(1))
                    .onErrorMap(e -> !(e instanceof SinExistenciasEnInventario), e -> new ServicioExternoNoDisponible("inventario", e));
        }
        public Mono<Void> liberar(Pedido p) {
            return cliente.post().uri("/liberar/{id}", p.id()).header("Idempotency-Key", p.id()).retrieve().bodyToMono(Void.class).timeout(Duration.ofSeconds(1))
                    .onErrorMap(e -> new ServicioExternoNoDisponible("inventario", e));
        }
    }

    /** Pagos: el límite de la aplicación (3 s) manda; este es el del cliente, algo menor para que falle primero con causa clara. */
    public static class ClienteDePagos implements Pagos {
        private final WebClient cliente;
        public ClienteDePagos(WebClient.Builder b, String url) { this.cliente = b.baseUrl(url).build(); }
        public Mono<Void> cobrar(Pedido p) {
            return cliente.post().uri("/cobrar/{id}", p.id()).header("Idempotency-Key", p.id()).retrieve()
                    .onStatus(s -> s.value() == HttpStatus.UNPROCESSABLE_ENTITY.value(), r -> Mono.error(new PagoRechazado(p.id())))
                    .bodyToMono(Void.class).timeout(Duration.ofSeconds(2))
                    .onErrorMap(e -> !(e instanceof PagoRechazado), e -> new ServicioExternoNoDisponible("pagos", e));
        }
        public Mono<Void> reembolsar(Pedido p) {
            return cliente.post().uri("/reembolsar/{id}", p.id()).header("Idempotency-Key", p.id()).retrieve().bodyToMono(Void.class).timeout(Duration.ofSeconds(2))
                    .onErrorMap(e -> new ServicioExternoNoDisponible("pagos", e));
        }
    }
}
