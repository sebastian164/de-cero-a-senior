import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import reactor.blockhound.BlockHound;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/** Los dos errores corregidos: una estrategia explícita para el exceso y la llamada bloqueante fuera del bucle. */
public class Main {

    public static void main(String[] args) {
        BlockHound.install();                                     // siempre: si algo bloquea donde no debe, que reviente
        System.out.println("BlockHound instalado");
        Schedulers.boundedElastic();                              // se crea aquí, no por primera vez desde dos hilos del bucle a la vez
        contrapresion();
        System.out.println();
        bloqueoEnElBucle();
    }

    // ---- 1. Contrapresión: la pantalla solo necesita el saldo más reciente ----

    static void contrapresion() {
        Scheduler pantalla = Schedulers.fromExecutorService(Executors.newSingleThreadExecutor(), "pantalla");
        AtomicInteger pintados = new AtomicInteger();
        AtomicInteger descartados = new AtomicInteger();
        try {
            Flux.interval(Duration.ofMillis(1))
                    .onBackpressureDrop(saldo -> descartados.incrementAndGet())   // sin demanda, el saldo viejo se tira
                    .publishOn(pantalla, 1)                       // la pantalla pide de uno en uno
                    .doOnNext(saldo -> {
                        pintar();
                        pintados.incrementAndGet();
                    })
                    .take(Duration.ofSeconds(3))
                    .blockLast();
            System.out.printf("1. Contrapresión: 3 s sin error. Pintados %d, descartados %d%n", pintados.get(), descartados.get());
        } finally {
            pantalla.dispose();
        }
    }

    static void pintar() {
        dormir(10);
    }

    // ---- 2. La consulta bloqueante, fuera del bucle de eventos ----

    static final Scheduler BUCLE = Schedulers.newParallel("bucle", 2);

    static void bloqueoEnElBucle() {
        long inicio = System.nanoTime();
        AtomicLong esperaDelPing = new AtomicLong();

        Flux<String> saldos = Flux.range(1, 8)
                .flatMap(i -> Mono.defer(() -> consultarSaldo(i)).subscribeOn(BUCLE));
        Mono<String> ping = Mono.delay(Duration.ofMillis(10))
                .flatMap(tick -> {
                    long enviado = System.nanoTime();
                    return Mono.fromSupplier(() -> "pong").subscribeOn(BUCLE)
                            .doOnNext(pong -> esperaDelPing.set(desde(enviado)));
                });

        Flux.merge(saldos, ping).blockLast();
        System.out.printf("2. Ocho consultas de 200 ms con un bucle de 2 hilos: %d ms en total%n", desde(inicio));
        System.out.printf("   El ping esperó %d ms%n", esperaDelPing.get());
        BUCLE.dispose();
    }

    /** El manejador. La llamada bloqueante va envuelta y se ejecuta en boundedElastic, un pool hecho para esperar. */
    static Mono<String> consultarSaldo(int i) {
        return Mono.fromCallable(() -> consultarSaldoConJdbc("cuenta-" + i))
                .subscribeOn(Schedulers.boundedElastic());
    }

    static String consultarSaldoConJdbc(String cuenta) {
        dormir(200);
        return cuenta + ": 1000000";
    }

    // ---- utilidades ----

    static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupcion);
        }
    }

    static long desde(long inicioNanos) {
        return (System.nanoTime() - inicioNanos) / 1_000_000;
    }
}
