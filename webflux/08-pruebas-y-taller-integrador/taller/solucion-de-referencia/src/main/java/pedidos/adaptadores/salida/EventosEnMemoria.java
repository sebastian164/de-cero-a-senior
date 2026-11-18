package pedidos.adaptadores.salida;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import pedidos.aplicacion.puertos.Eventos;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/** Un canal por pedido, que desaparece cuando el pedido termina o cuando se va el último suscriptor: no se acumulan canales de pedidos inactivos. */
public class EventosEnMemoria implements Eventos {
    private static final class Canal {
        final Sinks.Many<String> sink = Sinks.many().multicast().directBestEffort();
        final AtomicInteger suscriptores = new AtomicInteger();
    }
    private final Map<String, Canal> canales = new ConcurrentHashMap<>();

    public void emitir(String id, String estado) { Canal c = canales.get(id); if (c != null) c.sink.tryEmitNext(estado); }
    public void terminar(String id) { Canal c = canales.remove(id); if (c != null) c.sink.tryEmitComplete(); }
    public Flux<String> flujo(String id) {
        return Flux.defer(() -> {
            Canal c = canales.computeIfAbsent(id, k -> new Canal());
            c.suscriptores.incrementAndGet();
            return c.sink.asFlux().doFinally(s -> { if (c.suscriptores.decrementAndGet() == 0) canales.remove(id, c); });
        });
    }
    /** Para las pruebas: cuántos canales siguen abiertos. */
    public int canalesAbiertos() { return canales.size(); }
}
