package fondo;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Lo esencial de un pool: un número fijo de conexiones y un tiempo máximo de espera para conseguir una. */
public final class FondoDeConexiones {

    public static final class SinConexion extends RuntimeException {

        private static final long serialVersionUID = 1L;

        SinConexion(long esperaMs) {
            super("no hubo conexión disponible tras " + esperaMs + " ms");
        }
    }

    private final Semaphore libres;
    private final long esperaMaximaMs;
    private final AtomicInteger enUso = new AtomicInteger();
    private final AtomicInteger maximoEnUso = new AtomicInteger();
    private final AtomicInteger agotamientos = new AtomicInteger();

    public FondoDeConexiones(int tamano, long esperaMaximaMs) {
        this.libres = new Semaphore(tamano, true);
        this.esperaMaximaMs = esperaMaximaMs;
    }

    public void tomar() {
        try {
            if (!libres.tryAcquire(esperaMaximaMs, TimeUnit.MILLISECONDS)) {
                agotamientos.incrementAndGet();
                throw new SinConexion(esperaMaximaMs);
            }
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupcion);
        }
        maximoEnUso.accumulateAndGet(enUso.incrementAndGet(), Math::max);
    }

    public void devolver() {
        enUso.decrementAndGet();
        libres.release();
    }

    public int maximoEnUso() {
        return maximoEnUso.get();
    }

    public int agotamientos() {
        return agotamientos.get();
    }
}
