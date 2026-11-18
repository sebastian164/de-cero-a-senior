import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Mono;

/** La tasa del dólar. Ahora la consulta remota es perezosa: devolver el Mono no llama a nadie. */
public final class ServicioDeTasas {

    private final AtomicInteger llamadasRemotas = new AtomicInteger();
    private final Duration latencia;
    private volatile Long ultimaTasa;

    public ServicioDeTasas(Duration latencia) {
        this.latencia = latencia;
    }

    public Mono<Long> enCache() {
        return Mono.fromSupplier(() -> ultimaTasa);              // se lee al suscribirse, no al ensamblar
    }

    /** Hace de llamada HTTP no bloqueante. La llamada ocurre al suscribirse; la respuesta llega tras la latencia. */
    public Mono<Long> consultarRemoto() {
        return Mono.fromCallable(() -> {
                    llamadasRemotas.incrementAndGet();
                    return 4_000L;                                // pesos por dólar
                })
                .delayElement(latencia)
                .doOnNext(tasa -> ultimaTasa = tasa);
    }

    public int llamadasRemotas() {
        return llamadasRemotas.get();
    }
}
