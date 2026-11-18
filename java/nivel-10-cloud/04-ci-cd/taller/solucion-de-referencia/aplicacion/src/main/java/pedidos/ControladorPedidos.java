package pedidos;

import java.math.BigDecimal;
import java.net.URI;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pedidos")
public class ControladorPedidos {
    private final RepositorioPedidos pedidos;
    private final ServicioReportes reportes;
    private final ObjectProvider<KafkaTemplate<String, String>> kafka;
    private final String topico;

    public ControladorPedidos(RepositorioPedidos pedidos, ServicioReportes reportes,
                              ObjectProvider<KafkaTemplate<String, String>> kafka,
                              @Value("${app.kafka.topico:}") String topico) {
        this.pedidos = pedidos;
        this.reportes = reportes;
        this.kafka = kafka;
        this.topico = topico;
    }

    public record NuevoPedido(long clienteId, BigDecimal total) {
    }

    @PostMapping
    public ResponseEntity<Pedido> crear(@RequestBody NuevoPedido nuevo) throws Exception {
        Pedido guardado = pedidos.save(new Pedido(nuevo.clienteId(), nuevo.total()));
        if (!topico.isBlank()) {
            // Espera la confirmación del broker: si Kafka no está, el pedido falla y se nota.
            kafka.getObject().send(topico, guardado.getId().toString(),
                    "{\"pedidoId\":" + guardado.getId() + ",\"clienteId\":" + guardado.getClienteId() + "}").get();
        }
        return ResponseEntity.created(URI.create("/api/pedidos/" + guardado.getId())).body(guardado);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pedido> buscar(@PathVariable long id) {
        return pedidos.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/reporte/{clienteId}")
    public ServicioReportes.Reporte reporte(@PathVariable long clienteId) {
        return reportes.delCliente(clienteId);
    }
}
