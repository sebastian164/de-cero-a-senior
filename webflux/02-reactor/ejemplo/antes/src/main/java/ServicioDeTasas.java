import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Mono;

/** La tasa del dólar. Consultarla "en remoto" es caro; la última conocida queda en caché. */
public final class ServicioDeTasas {

    private final AtomicInteger llamadasRemotas = new AtomicInteger();
    private volatile Long ultimaTasa;

    public Mono<Long> enCache() {
        return Mono.justOrEmpty(ultimaTasa);
    }

    /** Hace de llamada HTTP: cuesta, y se cuenta. */
    public long consultarRemoto() {
        llamadasRemotas.incrementAndGet();
        ultimaTasa = 4_000L;                                     // pesos por dólar
        return ultimaTasa;
    }

    public int llamadasRemotas() {
        return llamadasRemotas.get();
    }
}
