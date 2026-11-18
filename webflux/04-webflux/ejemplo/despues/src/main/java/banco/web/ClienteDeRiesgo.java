package banco.web;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** El cliente del servicio de riesgo, con WebClient: pide, deja el hilo libre y sigue cuando llega la respuesta. */
@Component
public class ClienteDeRiesgo {

    static final Duration ESPERA_MAXIMA = Duration.ofSeconds(1);

    private final WebClient cliente;

    public ClienteDeRiesgo(WebClient.Builder constructor, @Value("${riesgo.url}") String urlDeRiesgo) {
        this.cliente = constructor.baseUrl(urlDeRiesgo).build();
    }

    public Mono<Riesgo> evaluar(String cuentaId) {
        return cliente.get().uri("/riesgo/{id}", cuentaId)
                .retrieve()                                       // 4xx y 5xx llegan como error
                .bodyToMono(Riesgo.class)
                .timeout(ESPERA_MAXIMA)                           // nunca esperar para siempre a otro
                .onErrorMap(RiesgoNoDisponible::new);             // un error técnico de fuera, con nombre propio
    }
}
