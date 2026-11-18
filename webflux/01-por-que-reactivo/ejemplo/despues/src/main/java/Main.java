import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * La misma carga resuelta de tres formas, y medida. Dos escenarios: esperar a otro servicio (E/S) y calcular (CPU).
 * La conclusión honesta no es "reactivo siempre": es "no bloquear un hilo escaso mientras se espera".
 */
public class Main {

    static final long LATENCIA_RIESGO_MS = 200;
    static final int NUCLEOS = Runtime.getRuntime().availableProcessors();

    static final Set<Long> hilos = ConcurrentHashMap.newKeySet();
    static int iteracionesPorCalculo;

    public static void main(String[] args) throws Exception {
        System.out.printf("Escenario 1: 200 peticiones que esperan %d ms al servicio de riesgo (E/S)%n", LATENCIA_RIESGO_MS);
        medir("Pool de 20 hilos de plataforma, bloqueando", () -> conPool(20, 200, Main::consultarRiesgoBloqueando));
        medir("Reactor, sin bloquear", () -> conReactor(200));
        medir("Hilos virtuales, bloqueando", () -> conHilosVirtuales(200, Main::consultarRiesgoBloqueando));

        System.out.println();
        System.out.println("Escenario 2: 10.000 peticiones iguales (el pool de 20 tardaría 100 s: no se mide)");
        medir("Reactor, sin bloquear", () -> conReactor(10_000));
        medir("Hilos virtuales, bloqueando", () -> conHilosVirtuales(10_000, Main::consultarRiesgoBloqueando));

        calibrarCalculo();
        System.out.println();
        System.out.printf("Escenario 3: 200 peticiones que calculan unos 20 ms de CPU cada una (%d núcleos)%n", NUCLEOS);
        medir("Pool de hilos de plataforma (uno por núcleo)", () -> conPool(NUCLEOS, 200, Main::calcularFirma));
        medir("Reactor, Schedulers.parallel()", () -> conReactorCalculando(200));
        medir("Hilos virtuales", () -> conHilosVirtuales(200, Main::calcularFirma));
    }

    // ---- Las tres formas de atender ----

    static void conPool(int tamano, int peticiones, Function<String, String> atender) throws Exception {
        try (ExecutorService servidor = Executors.newFixedThreadPool(tamano)) {
            esperarTodas(servidor, peticiones, atender);
        }
    }

    static void conHilosVirtuales(int peticiones, Function<String, String> atender) throws Exception {
        try (ExecutorService servidor = Executors.newVirtualThreadPerTaskExecutor()) {   // un hilo virtual por petición
            esperarTodas(servidor, peticiones, atender);
        }
    }

    /** Nadie espera dormido: la petición deja dicho qué hacer cuando llegue la respuesta, y el hilo queda libre. */
    static void conReactor(int peticiones) {
        Flux.range(1, peticiones)
                .flatMap(i -> consultarRiesgoSinBloquear("cuenta-" + i), peticiones)
                .blockLast();                                     // solo main espera, para poder medir
    }

    static void conReactorCalculando(int peticiones) {
        Flux.range(1, peticiones)
                .parallel(NUCLEOS)
                .runOn(Schedulers.parallel())
                .map(i -> calcularFirma("cuenta-" + i))
                .sequential()
                .blockLast();
    }

    // ---- Lo que hace cada petición ----

    /** Hace de llamada HTTP bloqueante: el hilo duerme hasta que llega la respuesta. */
    static String consultarRiesgoBloqueando(String cuenta) {
        hilos.add(Thread.currentThread().threadId());
        try {
            Thread.sleep(LATENCIA_RIESGO_MS);
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupcion);
        }
        return cuenta + ": BAJO";
    }

    /** Hace de llamada HTTP no bloqueante: un temporizador avisa cuando "llega" la respuesta. */
    static Mono<String> consultarRiesgoSinBloquear(String cuenta) {
        return Mono.delay(Duration.ofMillis(LATENCIA_RIESGO_MS))
                .map(tick -> {
                    hilos.add(Thread.currentThread().threadId());
                    return cuenta + ": BAJO";
                });
    }

    /** Trabajo de CPU puro: nadie espera a nadie, solo se calcula. */
    static String calcularFirma(String cuenta) {
        hilos.add(Thread.currentThread().threadId());
        byte[] valor = cuenta.getBytes(StandardCharsets.UTF_8);
        MessageDigest sha = sha256();
        for (int i = 0; i < iteracionesPorCalculo; i++) {
            valor = sha.digest(valor);
        }
        return cuenta + ":" + valor[0];
    }

    // ---- Medición ----

    interface Carga {
        void ejecutar() throws Exception;
    }

    static void medir(String nombre, Carga carga) throws Exception {
        hilos.clear();
        long inicio = System.nanoTime();
        carga.ejecutar();
        long ms = (System.nanoTime() - inicio) / 1_000_000;
        System.out.printf("  %-44s %6d ms  %6d hilos distintos%n", nombre, ms, hilos.size());
    }

    static void esperarTodas(ExecutorService servidor, int peticiones, Function<String, String> atender) throws Exception {
        List<Future<String>> respuestas = new ArrayList<>();
        for (int i = 1; i <= peticiones; i++) {
            String cuenta = "cuenta-" + i;
            Callable<String> peticion = () -> atender.apply(cuenta);
            respuestas.add(servidor.submit(peticion));
        }
        for (Future<String> respuesta : respuestas) {
            respuesta.get();
        }
    }

    /** Ajusta cuántas vueltas da el cálculo para que dure unos 20 ms en esta máquina. */
    static void calibrarCalculo() {
        iteracionesPorCalculo = 20_000;
        for (int i = 0; i < 5; i++) {
            calcularFirma("calentamiento");                       // que el JIT compile antes de medir
        }
        long inicio = System.nanoTime();
        calcularFirma("calibrar");
        double ms = (System.nanoTime() - inicio) / 1_000_000.0;
        iteracionesPorCalculo = (int) (iteracionesPorCalculo * 20 / ms);
    }

    static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException imposible) {
            throw new IllegalStateException(imposible);
        }
    }
}
