package pedidos;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Lo bloqueante se envuelve en fromCallable (perezoso) y se desvía a boundedElastic, que sí admite bloqueo. */
public final class PagosCorregidos implements Pagos {
    private final ClienteDePagosBloqueante cliente = new ClienteDePagosBloqueante();
    public final Set<String> hilos = ConcurrentHashMap.newKeySet();
    public Mono<Cobro> cobrar(Pedido p) {
        return Mono.fromCallable(() -> { hilos.add(Thread.currentThread().getName()); return cliente.cobrar(p); })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
