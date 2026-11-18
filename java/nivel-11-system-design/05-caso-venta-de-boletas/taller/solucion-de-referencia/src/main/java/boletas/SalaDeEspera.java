package boletas;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/** Sala de espera: admite como máximo 'porSegundo' usuarios por segundo, en orden de llegada. */
public class SalaDeEspera {
    public record Admision(long usuario, long llegoNanos, long admitidoNanos, long posicionAlLlegar) { }
    private final ConcurrentLinkedQueue<long[]> cola = new ConcurrentLinkedQueue<>();
    private final AtomicLong llegadas = new AtomicLong();
    private final java.util.List<Admision> admitidos = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final int porSegundo;
    private volatile boolean activa = true;
    private final Thread admisor;

    public SalaDeEspera(int porSegundo) {
        this.porSegundo = porSegundo;
        this.admisor = Thread.ofPlatform().daemon().start(() -> {
            long intervaloNanos = 1_000_000_000L / porSegundo;
            long proximo = System.nanoTime();
            while (activa) {
                long[] u = cola.poll();
                if (u == null) { java.util.concurrent.locks.LockSupport.parkNanos(1_000_000); proximo = Math.max(proximo, System.nanoTime()); continue; }
                long ahora = System.nanoTime();
                if (ahora < proximo) java.util.concurrent.locks.LockSupport.parkNanos(proximo - ahora);
                proximo += intervaloNanos;
                admitidos.add(new Admision(u[0], u[1], System.nanoTime(), u[2]));
            }
        });
    }

    /** Devuelve la posición en la fila ("estás en la posición 12.400"). */
    public synchronized long entrar(long usuario) {
        long pos = llegadas.incrementAndGet();
        cola.add(new long[]{usuario, System.nanoTime(), pos});
        return pos;
    }

    public java.util.List<Admision> admitidos() { return admitidos; }
    public void cerrar() { activa = false; admisor.interrupt(); }
}
