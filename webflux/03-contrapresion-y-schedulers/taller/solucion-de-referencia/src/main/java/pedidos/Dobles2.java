package pedidos;

import java.util.concurrent.ConcurrentHashMap;
import reactor.core.publisher.Mono;

/** Almacén de pedidos en memoria para las mediciones. */
final class Dobles2 {
    static final class Almacen implements Pedidos {
        final ConcurrentHashMap<String, Pedido> datos = new ConcurrentHashMap<>();
        Almacen(int n) { for (int i = 1; i <= n; i++) datos.put("p" + i, new Pedido("p" + i, 100, "PENDIENTE")); }
        public Mono<Pedido> buscar(String id) { return Mono.defer(() -> Mono.justOrEmpty(datos.get(id))); }
        public Mono<Pedido> guardar(Pedido p) { return Mono.defer(() -> { datos.put(p.id(), p); return Mono.just(p); }); }
    }
}
