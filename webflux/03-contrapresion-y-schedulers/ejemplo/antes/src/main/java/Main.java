import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import reactor.blockhound.BlockHound;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/** Dos errores que funcionan "casi siempre": un productor que ignora la demanda y JDBC dentro del bucle de eventos. */
public class Main {

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--blockhound")) {
            BlockHound.install();                                 // a partir de aquí, bloquear en un hilo no bloqueante es un error
            System.out.println("BlockHound instalado");
        }
        contrapresion();
        System.out.println();
        bloqueoEnElBucle();
    }

    // ---- 1. Contrapresión: el saldo cambia 1000 veces por segundo y la pantalla pinta 100 ----

    static void contrapresion() {
        Scheduler pantalla = Schedulers.fromExecutorService(Executors.newSingleThreadExecutor(), "pantalla");
        AtomicInteger pintados = new AtomicInteger();
        long inicio = System.nanoTime();
        try {
            Flux.interval(Duration.ofMillis(1))                   // un saldo nuevo cada milisegundo
                    .publishOn(pantalla)                          // la pantalla pide de 256 en 256
                    .doOnNext(saldo -> {
                        pintar();
                        pintados.incrementAndGet();
                    })
                    .blockLast();                                 // la pantalla está siempre abierta
            System.out.println("1. Contrapresión: terminó sin error, pintados " + pintados.get());
        } catch (RuntimeException error) {
            System.out.printf("1. Contrapresión: falló a los %d ms, tras pintar %d saldos%n", desde(inicio), pintados.get());
            System.out.println("   " + error.getMessage());
        } finally {
            pantalla.dispose();
        }
    }

    /** Pintar cuesta 10 ms. */
    static void pintar() {
        dormir(10);
    }

    // ---- 2. JDBC dentro del bucle de eventos ----

    /** Como el bucle de eventos de Netty: muy pocos hilos, que atienden a todos y nunca deberían bloquearse. */
    static final Scheduler BUCLE = Schedulers.newParallel("bucle", 2);

    static void bloqueoEnElBucle() {
        long inicio = System.nanoTime();
        AtomicLong esperaDelPing = new AtomicLong();

        Flux<String> saldos = Flux.range(1, 8)                    // ocho peticiones de saldo a la vez
                .flatMap(i -> Mono.defer(() -> consultarSaldo(i)).subscribeOn(BUCLE));
        Mono<String> ping = Mono.delay(Duration.ofMillis(10))     // y un ping, 10 ms después, que no toca la base
                .flatMap(tick -> {
                    long enviado = System.nanoTime();
                    return Mono.fromSupplier(() -> "pong").subscribeOn(BUCLE)
                            .doOnNext(pong -> esperaDelPing.set(desde(enviado)));
                });

        try {
            Flux.merge(saldos, ping).blockLast();
            System.out.printf("2. Ocho consultas de 200 ms con un bucle de 2 hilos: %d ms en total%n", desde(inicio));
            System.out.printf("   El ping, que no hace nada, esperó %d ms%n", esperaDelPing.get());
        } catch (Error | RuntimeException error) {
            System.out.println("2. Falló: " + error);
        } finally {
            BUCLE.dispose();
        }
    }

    /** El manejador de la petición. Llama a JDBC directamente: el hilo del bucle se queda esperando a la base. */
    static Mono<String> consultarSaldo(int i) {
        return Mono.just(consultarSaldoConJdbc("cuenta-" + i));
    }

    /** Hace de consulta JDBC: bloquea el hilo 200 ms. */
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
