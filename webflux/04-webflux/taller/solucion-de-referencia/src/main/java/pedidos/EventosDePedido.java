package pedidos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Un canal por pedido: cada cambio de estado se emite mientras ocurre; quien esté suscrito (SSE) lo recibe.
 * El canal se crea al primer suscriptor y desaparece cuando termina el pedido o cuando se va el último suscriptor:
 * consultar los eventos de un pedido que nadie confirma no deja nada acumulado.
 */
@Component
public class EventosDePedido {
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
    /** Suscriptores abiertos de ese pedido (las pruebas lo usan como señal de "ya estoy escuchando", en vez de dormir). */
    public int suscriptores(String id) { Canal c = canales.get(id); return c == null ? 0 : c.suscriptores.get(); }
    public int canalesAbiertos() { return canales.size(); }
}
