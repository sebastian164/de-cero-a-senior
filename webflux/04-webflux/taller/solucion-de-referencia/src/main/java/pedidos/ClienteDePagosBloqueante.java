package pedidos;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Mono;

/** SOLO PARA EL PASO 6: RestClient bloquea el hilo que lo llama. Se activa con pagos.cliente=restclient. */
@Component
@ConditionalOnProperty(name = "pagos.cliente", havingValue = "restclient")
public class ClienteDePagosBloqueante implements Pagos {
    private final RestClient cliente;

    public ClienteDePagosBloqueante(@Value("${pagos.url}") String url) {
        var fabrica = new JdkClientHttpRequestFactory(HttpClient.newHttpClient());
        fabrica.setReadTimeout(Duration.ofSeconds(2));
        this.cliente = RestClient.builder().baseUrl(url).requestFactory(fabrica).build();
    }

    public Mono<Void> cobrar(Pedido p) {
        return Mono.fromRunnable(() -> {             // se ejecuta en el hilo que suscribe: el bucle de eventos
            try { cliente.post().uri("/cobrar/{id}", p.id()).retrieve().toBodilessEntity(); }
            catch (HttpClientErrorException.UnprocessableEntity e) { throw new PagoRechazado(p.id()); }
            catch (RuntimeException e) { throw new ServicioExternoNoDisponible("pagos", e); }
        });
    }
}
