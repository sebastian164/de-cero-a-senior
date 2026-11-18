package pedidos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class AlmacenDePedidos {
    private final Map<String, Pedido> datos = new ConcurrentHashMap<>();

    public AlmacenDePedidos() {
        for (int i = 1; i <= 5000; i++) datos.put("p" + i, new Pedido("p" + i, 100, "PENDIENTE"));
        datos.put("sin-stock", new Pedido("sin-stock", 100, "PENDIENTE"));
        datos.put("rechazado", new Pedido("rechazado", 100, "PENDIENTE"));
    }
    public Mono<Pedido> buscar(String id) { return Mono.defer(() -> Mono.justOrEmpty(datos.get(id))); }
    public Mono<Pedido> guardar(Pedido p) { return Mono.fromSupplier(() -> { datos.put(p.id(), p); return p; }); }
}
