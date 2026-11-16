package servicio;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** Dependencia OPCIONAL, protegida: cortacircuitos + mamparo + tiempo límite del cliente + respaldo honesto. */
@Service
public class Recomendaciones {

    public record Resultado(List<String> items, boolean degradado) { }

    private final RestClient cliente;

    Recomendaciones(@Qualifier("recomendacionesRapido") RestClient cliente) { this.cliente = cliente; }

    @CircuitBreaker(name = "recomendaciones", fallbackMethod = "masVendidos")
    @Bulkhead(name = "recomendaciones")
    public Resultado recomendar(long id) {
        cliente.get().uri("/datos/{id}", id).retrieve().body(Map.class);
        return new Resultado(List.of("recomendado-para-" + id), false);
    }

    /** Respaldo adecuado: contenido razonable y marcado como degradado. */
    public Resultado masVendidos(long id, Throwable causa) {
        return new Resultado(List.of("más-vendido-1", "más-vendido-2"), true);
    }
}
