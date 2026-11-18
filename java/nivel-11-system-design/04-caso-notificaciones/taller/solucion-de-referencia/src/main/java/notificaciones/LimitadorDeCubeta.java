package notificaciones;

import java.util.concurrent.locks.LockSupport;

/** Cubeta de fichas: 'fichasPorSegundo' de ritmo sostenido y hasta 'capacidad' de ráfaga. adquirir() espera si no hay ficha. */
public class LimitadorDeCubeta {
    private final double fichasPorSegundo;
    private final double capacidad;
    private double fichas;
    private long ultimaNanos;

    public LimitadorDeCubeta(double fichasPorSegundo, int capacidad) {
        this.fichasPorSegundo = fichasPorSegundo; this.capacidad = capacidad; this.fichas = capacidad; this.ultimaNanos = System.nanoTime();
    }

    public void adquirir() {
        while (true) {
            long esperaNanos;
            synchronized (this) {
                long ahora = System.nanoTime();
                fichas = Math.min(capacidad, fichas + (ahora - ultimaNanos) / 1e9 * fichasPorSegundo);
                ultimaNanos = ahora;
                if (fichas >= 1) { fichas -= 1; return; }
                esperaNanos = (long) ((1 - fichas) / fichasPorSegundo * 1e9);
            }
            LockSupport.parkNanos(Math.max(esperaNanos, 50_000));
        }
    }
}
