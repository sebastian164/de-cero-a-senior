package pedidos;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnProperty(name = "pagos.cliente", havingValue = "webclient", matchIfMissing = true)
public class ClienteDePagos implements Pagos {
    /** 2 s: un cobro es más lento que una reserva (~300 ms normal); más allá, es mejor fallar que dejar al cliente esperando. */
    static final Duration LIMITE = Duration.ofSeconds(2);
    private final WebClient cliente;

    public ClienteDePagos(WebClient.Builder b, @Value("${pagos.url}") String url) { this.cliente = b.baseUrl(url).build(); }

    public Mono<Void> cobrar(Pedido p) {
        return cliente.post().uri("/cobrar/{id}", p.id()).retrieve()
                .onStatus(s -> s.value() == HttpStatus.UNPROCESSABLE_ENTITY.value(), r -> Mono.error(new PagoRechazado(p.id())))
                .bodyToMono(Void.class)
                .timeout(LIMITE)
                .onErrorMap(e -> !(e instanceof PagoRechazado), e -> new ServicioExternoNoDisponible("pagos", e));
    }
}
