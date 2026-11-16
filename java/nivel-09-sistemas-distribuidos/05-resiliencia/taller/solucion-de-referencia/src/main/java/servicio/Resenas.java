package servicio;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** Segunda dependencia OPCIONAL: si cae, la página se muestra sin reseñas y lo dice. */
@Service
public class Resenas {

    public record Resultado(List<String> items, boolean disponibles) { }

    private final RestClient cliente;

    Resenas(@Qualifier("clienteResenas") RestClient cliente) { this.cliente = cliente; }

    @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(name = "resenas", fallbackMethod = "sinResenas")
    public Resultado listar(long id) {
        cliente.get().uri("/datos/{id}", id).retrieve().body(Map.class);
        return new Resultado(List.of("reseña-de-" + id), true);
    }

    public Resultado sinResenas(long id, Throwable causa) {
        return new Resultado(List.of(), false);          // honesto: "disponibles: false", no "0 reseñas"
    }
}
