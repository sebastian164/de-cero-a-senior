import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Tres usos de Redis que no son caché, cada uno escrito de la forma más directa. Con un usuario, todo funciona. */
public class Main {

    public static void main(String[] args) {
        RedisClient cliente = RedisClient.create("redis://localhost:6379");
        try (StatefulRedisConnection<String, String> conexion = cliente.connect()) {
            RedisCommands<String, String> redis = conexion.sync();
            redis.flushdb();                                     // solo porque es el Redis del taller
            idempotencia(redis);
            limiteDeTasa(redis);
            cerrojo(redis);
        } finally {
            cliente.shutdown();
        }
    }

    private static void idempotencia(RedisCommands<String, String> redis) {
        System.out.println("1. Idempotencia: el móvil no recibió respuesta y reenvió el mismo pago 5 veces");
        Pasarela pasarela = new Pasarela();
        Idempotencia pagos = new Idempotencia(redis, pasarela);
        List<String> respuestas = aLaVez(5, () -> pagos.pagar("pago-77", "cuenta-1", 8_000_000));
        System.out.println("   respuestas: " + respuestas);
        System.out.println("   cobros en la pasarela: " + pasarela.cobros());
        System.out.println("   un reintento al rato: " + pagos.pagar("pago-77", "cuenta-1", 8_000_000));
        System.out.println("   la clave caduca en: " + redis.ttl("pago:pago-77") + " (-1 es nunca)");
    }

    private static void limiteDeTasa(RedisCommands<String, String> redis) {
        System.out.println("2a. Límite de 5 por segundo: una ráfaga justo en el cambio de ventana");
        LimiteDeTasa limite = new LimiteDeTasa(redis);
        limite.permitir("cliente-1");                            // t = 0: empieza la ventana
        dormir(900);
        int aceptadas = contarAceptadas(limite, "cliente-1", 4); // t = 900 ms
        dormir(150);
        aceptadas += contarAceptadas(limite, "cliente-1", 5);    // t = 1.050 ms
        System.out.println("   entre t=900 ms y t=1.050 ms se aceptaron " + aceptadas + " de 9");

        System.out.println("2b. El proceso se cae entre contar y poner la caducidad");
        limite.caerseEnLaProxima();
        try {
            limite.permitir("cliente-2");
        } catch (IllegalStateException caida) {
            System.out.println("   " + caida.getMessage());
        }
        System.out.println("   6 peticiones: aceptadas " + contarAceptadas(limite, "cliente-2", 6));
        dormir(1_200);
        System.out.println("   1,2 s después, otra: " + (limite.permitir("cliente-2") ? "aceptada" : "rechazada")
                + "; la clave caduca en: " + redis.ttl("tasa:cliente-2") + " (-1 es nunca)");
    }

    private static void cerrojo(RedisCommands<String, String> redis) {
        System.out.println("3. Cerrojo para el cierre del día, con vida de 1 s");
        Cerrojo cerrojo = new Cerrojo(redis);
        LibroMayor libro = new LibroMayor();

        System.out.println("   A toma el cerrojo: " + siNo(cerrojo.tomar("cierre", 1_000)));
        dormir(1_500);                                           // A se congela: una pausa larga del recolector de basura
        System.out.println("   B toma el cerrojo: " + siNo(cerrojo.tomar("cierre", 1_000)));
        libro.escribir("B", "cierre del 15 de noviembre");
        System.out.println("   A despierta y escribe: " + (libro.escribir("A", "cierre del 15 de noviembre") ? "aceptado" : "rechazado"));
        cerrojo.soltar("cierre");                                // A suelta… el cerrojo de B
        System.out.println("   C toma el cerrojo mientras B sigue trabajando: " + siNo(cerrojo.tomar("cierre", 1_000)));
        System.out.println("   el libro: " + libro.asientos());
    }

    private static int contarAceptadas(LimiteDeTasa limite, String cliente, int peticiones) {
        int aceptadas = 0;
        for (int i = 0; i < peticiones; i++) {
            if (limite.permitir(cliente)) {
                aceptadas++;
            }
        }
        return aceptadas;
    }

    private static List<String> aLaVez(int veces, Callable<String> peticion) {
        List<Future<String>> enviadas = new ArrayList<>();
        try (ExecutorService ejecutor = Executors.newFixedThreadPool(veces)) {
            for (int i = 0; i < veces; i++) {
                enviadas.add(ejecutor.submit(peticion));
            }
        }
        List<String> respuestas = new ArrayList<>();
        for (Future<String> enviada : enviadas) {
            try {
                respuestas.add(enviada.get());
            } catch (InterruptedException | ExecutionException fallo) {
                throw new IllegalStateException(fallo);
            }
        }
        Collections.sort(respuestas);
        return respuestas;
    }

    private static String siNo(boolean valor) {
        return valor ? "sí" : "no";
    }

    private static void dormir(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
        }
    }
}
