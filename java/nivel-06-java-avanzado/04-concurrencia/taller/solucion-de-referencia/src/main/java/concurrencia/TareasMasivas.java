package concurrencia;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 10.000 tareas que duermen 1 s: pool fijo de 200 hilos frente a hilos virtuales. */
public class TareasMasivas {

    public static Duration medir(ExecutorService ejecutor, int tareas) {
        long inicio = System.nanoTime();
        try (ejecutor) {
            for (int i = 0; i < tareas; i++) {
                ejecutor.submit(() -> { Thread.sleep(1_000); return null; });
            }
        }                                           // close() espera a que terminen todas
        return Duration.ofNanos(System.nanoTime() - inicio);
    }

    public static void main(String[] args) {
        int tareas = 10_000;
        System.out.println("hilos virtuales: " + medir(Executors.newVirtualThreadPerTaskExecutor(), tareas).toMillis() + " ms");
        System.out.println("pool fijo 200:   " + medir(Executors.newFixedThreadPool(200), tareas).toMillis() + " ms");
    }
}
