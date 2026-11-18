import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Un hilo por petición, con un pool fijo de hilos de plataforma: el modelo clásico de un servidor como Tomcat.
 * Cada petición consulta el servicio de riesgo, que tarda 200 ms en responder. Mientras espera, el hilo no hace nada.
 */
public class Main {

    static final int PETICIONES = 200;
    static final int HILOS = 20;                                  // el pool del servidor
    static final long LATENCIA_RIESGO_MS = 200;

    static final Set<String> hilosUsados = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) throws Exception {
        long inicio = System.nanoTime();
        List<Future<String>> respuestas = new ArrayList<>();
        try (ExecutorService servidor = Executors.newFixedThreadPool(HILOS)) {
            for (int i = 1; i <= PETICIONES; i++) {
                String cuenta = "cuenta-" + i;
                respuestas.add(servidor.submit(() -> atender(cuenta)));
            }
            for (Future<String> respuesta : respuestas) {
                respuesta.get();
            }
        }
        long ms = (System.nanoTime() - inicio) / 1_000_000;

        System.out.printf("%d peticiones, cada una espera %d ms al servicio de riesgo%n", PETICIONES, LATENCIA_RIESGO_MS);
        System.out.printf("Pool de %d hilos de plataforma: %d ms en total, %d hilos usados%n", HILOS, ms, hilosUsados.size());
        System.out.printf("Tiempo de CPU útil por petición: casi cero. Tiempo de hilo ocupado: %d ms%n", LATENCIA_RIESGO_MS);
    }

    static String atender(String cuenta) throws InterruptedException {
        hilosUsados.add(Thread.currentThread().getName());
        return consultarRiesgo(cuenta);
    }

    /** Hace de llamada HTTP bloqueante: el hilo se queda dormido hasta que llega la respuesta. */
    static String consultarRiesgo(String cuenta) throws InterruptedException {
        Thread.sleep(LATENCIA_RIESGO_MS);
        return cuenta + ": BAJO";
    }
}
