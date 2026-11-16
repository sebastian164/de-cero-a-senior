package trazas.a;

import io.micrometer.tracing.Tracer;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/** Servicio A: recibe la petición, llama a B por HTTP y publica un evento en Kafka. */
@SpringBootApplication
@RestController
public class ServicioA {

    private final RestClient b;
    private final KafkaTemplate<String, String> kafka;
    private final Tracer tracer;

    // Un RestClient construido a partir del Builder de Spring propaga el contexto automáticamente.
    ServicioA(RestClient.Builder builder, KafkaTemplate<String, String> kafka, Tracer tracer,
              @org.springframework.beans.factory.annotation.Value("${servicio-b.url}") String urlB) {
        this.b = builder.baseUrl(urlB).build();
        this.kafka = kafka;
        this.tracer = tracer;
    }

    @GetMapping("/pedidos/{id}")
    Map<String, Object> pedido(@PathVariable long id) {
        Map<String, Object> inventario = b.get().uri("/inventario/{id}", id).retrieve().body(Map.class);
        kafka.send("pedidos.creados", String.valueOf(id), "{\"pedidoId\":" + id + "}");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("pedidoId", id);
        r.put("inventario", inventario);
        r.put("traceId", tracer.currentSpan() == null ? null : tracer.currentSpan().context().traceId());
        return r;
    }

    public static void main(String[] args) {
        SpringApplication.run(ServicioA.class, args);
    }
}
