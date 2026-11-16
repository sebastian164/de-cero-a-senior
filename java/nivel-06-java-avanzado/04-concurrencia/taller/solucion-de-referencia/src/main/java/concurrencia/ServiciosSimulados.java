package concurrencia;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public final class ServiciosSimulados {
    private ServiciosSimulados() { }

    public record Resumen(String cliente, String pedidos, String puntos) { }

    static String llamar(String respuesta, long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
        return respuesta;
    }

    /** Tres servicios de 300, 500 y 800 ms consultados en paralelo y combinados. */
    public static Resumen consultar(ExecutorService pool) {
        CompletableFuture<String> cliente = CompletableFuture.supplyAsync(() -> llamar("Ana", 300), pool);
        CompletableFuture<String> pedidos = CompletableFuture.supplyAsync(() -> llamar("3 pedidos", 500), pool);
        CompletableFuture<String> puntos = CompletableFuture.supplyAsync(() -> llamar("120 puntos", 800), pool);
        return cliente.thenCombine(pedidos, (c, p) -> new String[]{c, p})
                .thenCombine(puntos, (cp, pts) -> new Resumen(cp[0], cp[1], pts))
                .orTimeout(2, TimeUnit.SECONDS)
                .join();
    }
}
