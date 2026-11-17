package comun;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class Hilos {
    private Hilos() {}

    /** Lanza n tareas que arrancan a la vez (barrera) y devuelve sus resultados en orden. Con n hilos reales. */
    public static <T> List<T> aLaVez(int n, java.util.function.IntFunction<Callable<T>> tarea) {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CyclicBarrier barrera = new CyclicBarrier(n);
        try {
            List<Future<T>> futuros = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Callable<T> t = tarea.apply(i);
                futuros.add(pool.submit(() -> { barrera.await(); return t.call(); }));
            }
            List<T> res = new ArrayList<>();
            for (Future<T> f : futuros) {
                try { res.add(f.get()); } catch (Exception e) { throw new RuntimeException(e); }
            }
            return res;
        } finally {
            pool.shutdownNow();
        }
    }

    /** Como aLaVez pero con un tope de hilos distinto del número de tareas (sin barrera). */
    public static <T> List<T> conPool(int hilos, int tareas, java.util.function.IntFunction<Callable<T>> tarea) {
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        try {
            List<Future<T>> futuros = new ArrayList<>();
            for (int i = 0; i < tareas; i++) futuros.add(pool.submit(tarea.apply(i)));
            List<T> res = new ArrayList<>();
            for (Future<T> f : futuros) {
                try { res.add(f.get()); } catch (Exception e) { throw new RuntimeException(e); }
            }
            return res;
        } finally {
            pool.shutdownNow();
        }
    }

    public static void dormir(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
    }
}
