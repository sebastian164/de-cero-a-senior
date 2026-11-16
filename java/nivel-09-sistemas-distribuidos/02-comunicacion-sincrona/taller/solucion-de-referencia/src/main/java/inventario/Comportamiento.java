package inventario;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/** Fallos inyectables desde las pruebas. */
@Component
public class Comportamiento {

    public volatile double probabilidadLento = 0;
    public volatile long demoraLentaMs = 1000;
    public volatile CountDownLatch colgado;                 // si no es null, las peticiones esperan a que se libere
    public volatile long perderRespuestaMs = 0;             // tras procesar el cobro, demora la respuesta
    public final AtomicInteger llamadasExistencias = new AtomicInteger();
    public final List<Long> llegadasNanos = new CopyOnWriteArrayList<>();

    public void reiniciar() {
        probabilidadLento = 0;
        colgado = null;
        perderRespuestaMs = 0;
        llamadasExistencias.set(0);
        llegadasNanos.clear();
    }

    void alLlegar() throws InterruptedException {
        llamadasExistencias.incrementAndGet();
        llegadasNanos.add(System.nanoTime());
        CountDownLatch c = colgado;
        if (c != null) {
            c.await(30, TimeUnit.SECONDS);
        } else if (ThreadLocalRandom.current().nextDouble() < probabilidadLento) {
            Thread.sleep(demoraLentaMs);
        }
    }

    void antesDeResponderCobro() throws InterruptedException {
        if (perderRespuestaMs > 0) {
            Thread.sleep(perderRespuestaMs);    // el cobro ya se confirmó; la respuesta llega tarde y el cliente ya se fue
        }
    }
}
