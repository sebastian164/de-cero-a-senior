import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Taller 01: "confirmar pedido" = inventario (80 ms) + pagos (150 ms) + guardar (10 ms), en tres modelos. */
public class Confirmaciones {

    static final long INVENTARIO_MS = 80, PAGOS_MS = 150, GUARDAR_MS = 10;
    static int iteraciones = 1;          // vueltas de SHA-256 de un paso de CPU; 0 = sin paso de CPU

    static void dormir(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }

    static String firmar(String pedido) {
        byte[] v = pedido.getBytes(StandardCharsets.UTF_8);
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            for (int i = 0; i < iteraciones; i++) v = sha.digest(v);
        } catch (Exception e) { throw new IllegalStateException(e); }
        return pedido;
    }

    /** Hilos virtuales de apoyo para la variante "con hilos en paralelo": la consulta de inventario corre en el suyo mientras el hilo de la confirmación consulta pagos. */
    static final ExecutorService APOYO = Executors.newVirtualThreadPerTaskExecutor();

    // --- Versiones con hilos (bloquean): inventario y pagos en secuencia, o en paralelo con dos hilos; luego guardar ---
    static String confirmarBloqueando(String pedido, boolean cpu, Semaphore guardar) { return confirmarBloqueando(pedido, cpu, guardar, false); }

    static String confirmarBloqueando(String pedido, boolean cpu, Semaphore guardar, boolean paralelo) {
        if (paralelo) {
            Future<?> inventario = APOYO.submit(() -> dormir(INVENTARIO_MS));
            dormir(PAGOS_MS);
            try { inventario.get(); } catch (Exception e) { throw new IllegalStateException(e); }
        } else {
            dormir(INVENTARIO_MS);
            dormir(PAGOS_MS);
        }
        if (cpu) firmar(pedido);
        if (guardar != null) {
            guardar.acquireUninterruptibly();
            try { dormir(GUARDAR_MS); } finally { guardar.release(); }
        } else dormir(GUARDAR_MS);
        return pedido + ":CONFIRMADO";
    }

    // --- Versión Reactor: inventario y pagos en paralelo con Mono.zip ---
    static Mono<String> confirmarReactivo(String pedido, boolean cpu) {
        Mono<String> inventario = Mono.delay(Duration.ofMillis(INVENTARIO_MS)).thenReturn("reserva");
        Mono<String> pagos = Mono.delay(Duration.ofMillis(PAGOS_MS)).thenReturn("cobro");
        return Mono.zip(inventario, pagos)
                .publishOn(Schedulers.parallel())
                .map(t -> cpu ? firmar(pedido) : pedido)
                .flatMap(p -> Mono.delay(Duration.ofMillis(GUARDAR_MS)).thenReturn(p + ":CONFIRMADO"));
    }

    // --- Ejecutores: devuelven las respuestas ordenadas ---
    static List<String> conPool(int n, int hilos, boolean cpu, Semaphore guardar) throws Exception { return conPool(n, hilos, cpu, guardar, false); }
    static List<String> conVirtuales(int n, boolean cpu, Semaphore guardar) throws Exception { return conVirtuales(n, cpu, guardar, false); }

    static List<String> conPool(int n, int hilos, boolean cpu, Semaphore guardar, boolean paralelo) throws Exception {
        try (ExecutorService s = Executors.newFixedThreadPool(hilos)) { return esperar(s, n, cpu, guardar, paralelo); }
    }

    static List<String> conVirtuales(int n, boolean cpu, Semaphore guardar, boolean paralelo) throws Exception {
        try (ExecutorService s = Executors.newVirtualThreadPerTaskExecutor()) { return esperar(s, n, cpu, guardar, paralelo); }
    }

    static List<String> esperar(ExecutorService s, int n, boolean cpu, Semaphore guardar, boolean paralelo) throws Exception {
        List<Future<String>> fs = new ArrayList<>();
        for (int i = 1; i <= n; i++) { String p = "pedido-" + i; fs.add(s.submit(() -> confirmarBloqueando(p, cpu, guardar, paralelo))); }
        List<String> r = new ArrayList<>();
        for (Future<String> f : fs) r.add(f.get());
        return r;
    }

    /** Reactor: inventario y pagos en paralelo (zip) para los n pedidos a la vez; después, guardar sin límite (limiteGuardar = 0)
     *  o con solo 'limiteGuardar' guardados simultáneos (un flatMapSequential de esa concurrencia sobre el paso de guardar). */
    static List<String> conReactor(int n, boolean cpu, int limiteGuardar) {
        Flux<String> flujo = Flux.range(1, n).map(i -> "pedido-" + i).flatMap(p -> {
            Mono<String> inv = Mono.delay(Duration.ofMillis(INVENTARIO_MS)).thenReturn("r");
            Mono<String> pag = Mono.delay(Duration.ofMillis(PAGOS_MS)).thenReturn("c");
            return Mono.zip(inv, pag).publishOn(Schedulers.parallel()).map(t -> cpu ? firmar(p) : p);
        }, n);
        if (limiteGuardar > 0) {
            // solo 'limiteGuardar' guardados a la vez; los demás esperan en cola, sin ocupar hilos
            flujo = flujo.flatMapSequential(p -> Mono.delay(Duration.ofMillis(GUARDAR_MS)).thenReturn(p), limiteGuardar);
        } else {
            flujo = flujo.flatMap(p -> Mono.delay(Duration.ofMillis(GUARDAR_MS)).thenReturn(p), n);
        }
        return flujo.map(p -> p + ":CONFIRMADO").collectList().block().stream().sorted(Confirmaciones::porNumero).toList();
    }

    static int porNumero(String a, String b) { return Integer.compare(num(a), num(b)); }
    static int num(String s) { return Integer.parseInt(s.substring(7, s.indexOf(':'))); }

    // --- Medición ---
    interface Carga { List<String> run() throws Exception; }

    static long medir(String nombre, Carga c) throws Exception {
        long t0 = System.nanoTime();
        List<String> r = c.run();
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("  %-52s %7d ms  (%d confirmaciones)%n", nombre, ms, r.size());
        return ms;
    }

    static void calibrar30ms() {
        iteraciones = 20_000;
        for (int i = 0; i < 20; i++) firmar("calentar");
        long t0 = System.nanoTime(); firmar("calibrar");
        double ms = (System.nanoTime() - t0) / 1e6;
        iteraciones = (int) Math.max(1, iteraciones * 30 / ms);
    }

    /** Una confirmación aislada, con calentamiento previo; mediana de 15 repeticiones. */
    static long aislada(Callable<?> una) throws Exception {
        for (int i = 0; i < 10; i++) una.call();
        long[] t = new long[15];
        for (int i = 0; i < t.length; i++) { long t0 = System.nanoTime(); una.call(); t[i] = (System.nanoTime() - t0) / 1_000_000; }
        java.util.Arrays.sort(t);
        return t[t.length / 2];
    }

    public static void main(String[] args) throws Exception {
        int nucleos = Runtime.getRuntime().availableProcessors();
        System.out.println("Núcleos: " + nucleos);
        calibrar30ms();
        // Calentamiento: las tres versiones y la paralela, para que la primera medida no pague la carga de clases ni la compilación.
        for (int i = 0; i < 2; i++) {
            conPool(300, 50, false, null); conPool(300, 50, false, null, true); conVirtuales(300, false, null); conVirtuales(300, false, null, true); conReactor(300, false, 0);
        }
        System.out.println("Una confirmación aislada (mediana de 15, tras calentar):");
        long pool = aislada(() -> confirmarBloqueando("pedido-1", false, null));
        long poolPar = aislada(() -> confirmarBloqueando("pedido-1", false, null, true));
        long reactiva = aislada(() -> confirmarReactivo("pedido-1", false).block());
        System.out.printf("  Hilos, secuencia: %d ms | Hilos, inventario y pagos en paralelo: %d ms | Reactor (zip): %d ms%n", pool, poolPar, reactiva);
        System.out.println("Una confirmación reactiva: " + reactiva + " ms (ronda 160, no 240)");
        for (int n : new int[]{500, 5000}) {
            System.out.println("Pasos 2-3: " + n + " confirmaciones, sin CPU");
            medir("Pool de 50 hilos, en secuencia", () -> conPool(n, 50, false, null));
            medir("Pool de 50 hilos, inventario y pagos en paralelo", () -> conPool(n, 50, false, null, true));
            medir("Hilos virtuales, en secuencia", () -> conVirtuales(n, false, null));
            medir("Hilos virtuales, inventario y pagos en paralelo", () -> conVirtuales(n, false, null, true));
            medir("Reactor (zip inventario+pagos)", () -> conReactor(n, false, 0));
        }
        System.out.println("Paso 4: 500 confirmaciones con 30 ms de CPU (firma)");
        medir("Pool de 50 hilos, en secuencia", () -> conPool(500, 50, true, null));
        medir("Pool de 50 hilos, en paralelo", () -> conPool(500, 50, true, null, true));
        medir("Hilos virtuales, en secuencia", () -> conVirtuales(500, true, null));
        medir("Hilos virtuales, en paralelo", () -> conVirtuales(500, true, null, true));
        medir("Reactor", () -> conReactor(500, true, 0));
        System.out.println("Paso 5: 500 confirmaciones, solo 20 a la vez en 'guardar'");
        medir("Pool de 50 hilos + Semaphore(20), en secuencia", () -> conPool(500, 50, false, new Semaphore(20)));
        medir("Pool de 50 hilos + Semaphore(20), en paralelo", () -> conPool(500, 50, false, new Semaphore(20), true));
        medir("Hilos virtuales + Semaphore(20), en secuencia", () -> conVirtuales(500, false, new Semaphore(20)));
        medir("Hilos virtuales + Semaphore(20), en paralelo", () -> conVirtuales(500, false, new Semaphore(20), true));
        medir("Reactor + flatMap(concurrencia 20) al guardar", () -> conReactor(500, false, 20));
        APOYO.shutdown();
    }
}
