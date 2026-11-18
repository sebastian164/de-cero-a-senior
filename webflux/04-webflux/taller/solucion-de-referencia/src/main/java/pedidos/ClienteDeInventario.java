package pedidos;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class ClienteDeInventario implements Inventario {
    /** 1 s: el servicio responde en ~300 ms; 3 veces su latencia normal absorbe un mal momento sin dejar colgada la petición. */
    static final Duration LIMITE = Duration.ofSeconds(1);
    private final WebClient cliente;

    public ClienteDeInventario(WebClient.Builder b, @Value("${inventario.url}") String url) { this.cliente = b.baseUrl(url).build(); }

    public Mono<Void> reservar(Pedido p) {
        return cliente.post().uri("/reservar/{id}", p.id()).retrieve()
                .onStatus(s -> s.value() == HttpStatus.CONFLICT.value(), r -> Mono.error(new SinExistencias(p.id())))
                .bodyToMono(Void.class)
                .timeout(LIMITE)
                .onErrorMap(e -> !(e instanceof SinExistencias), e -> new ServicioExternoNoDisponible("inventario", e));
    }
}
