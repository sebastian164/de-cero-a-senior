package concurrencia;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

final class Paralelo {

    interface Tarea {
        void ejecutar(int i) throws Exception;
    }

    record Resultado(int exitos, int fallos, long milisegundos, List<Throwable> errores) {}

    static Resultado correr(int hilos, Tarea tarea) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<?>> f = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            int n = i;
            f.add(pool.submit(() -> {
                salida.await();
                tarea.ejecutar(n);
                return null;
            }));
        }
        long t0 = System.nanoTime();
        salida.countDown();
        int ok = 0;
        List<Throwable> errores = new ArrayList<>();
        for (Future<?> x : f) {
            try {
                x.get(60, TimeUnit.SECONDS);
                ok++;
            } catch (java.util.concurrent.ExecutionException e) {
                errores.add(e.getCause());
            }
        }
        pool.shutdown();
        return new Resultado(ok, errores.size(), (System.nanoTime() - t0) / 1_000_000, errores);
    }
}
