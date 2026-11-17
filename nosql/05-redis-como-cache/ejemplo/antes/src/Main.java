import banco.adaptadores.cache.CuentasConCache;
import banco.adaptadores.salida.memoria.AlmacenEnMemoria;
import banco.aplicacion.ServicioDeCuentas;
import banco.aplicacion.puertos.entrada.ConsultarCuenta;
import banco.aplicacion.puertos.entrada.Depositar;
import banco.aplicacion.puertos.salida.Reloj;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** El banco con una caché delante de la consulta de cuentas. Las consultas repetidas vuelan. */
public class Main {

    private static final Reloj RELOJ = () -> Instant.parse("2026-11-15T10:00:00Z");
    private static final Locale ES = Locale.forLanguageTag("es-CO");

    public static void main(String[] args) {
        RedisClient cliente = RedisClient.create("redis://localhost:6379");
        try (StatefulRedisConnection<String, String> conexion = cliente.connect()) {
            RedisCommands<String, String> redis = conexion.sync();
            redis.flushdb();                                     // solo porque es el Redis del taller

            AlmacenEnMemoria almacen = new AlmacenEnMemoria();
            BaseLenta base = new BaseLenta(almacen);
            ServicioDeCuentas servicio = new ServicioDeCuentas(base, almacen, almacen, almacen, RELOJ, almacen);
            ConsultarCuenta consultas = new CuentasConCache(servicio, redis);
            Depositar depositos = servicio;                      // los depósitos van directo al banco
            String ana = servicio.abrir("Ana", 100_000_000).id();
            base.lecturasDesdeLaUltimaVez();

            System.out.println("1. Ana consulta su saldo 100 veces");
            for (int i = 0; i < 100; i++) {
                consultas.porId(ana);
            }
            System.out.printf(ES, "   lecturas a la base: %d de 100 consultas%n", base.lecturasDesdeLaUltimaVez());

            System.out.println("2. Ana deposita 50.000 pesos y vuelve a consultar");
            depositos.depositar(ana, 5_000_000);
            System.out.printf(ES, "   la caché dice %,d pesos; el banco tiene %,d; la clave caduca en: %d (-1 es nunca)%n",
                    consultas.porId(ana).orElseThrow().saldoCentavos() / 100,
                    servicio.porId(ana).orElseThrow().saldoCentavos() / 100, redis.ttl(ana));
            base.lecturasDesdeLaUltimaVez();

            System.out.println("3. 100 consultas de una cuenta que no existe");
            for (int i = 0; i < 100; i++) {
                consultas.porId("cuenta-inventada");
            }
            System.out.printf(ES, "   lecturas a la base: %d%n", base.lecturasDesdeLaUltimaVez());

            System.out.println("4. La clave de Ana desaparece (se desalojó o Redis se reinició) y llegan 50 consultas a la vez");
            redis.del(ana);
            aLaVez(50, () -> consultas.porId(ana));
            System.out.printf(ES, "   lecturas a la base: %d%n", base.lecturasDesdeLaUltimaVez());

            System.out.println("Claves en Redis: " + new TreeSet<>(redis.keys("*")));
        } finally {
            cliente.shutdown();
        }
    }

    private static void aLaVez(int peticiones, Runnable consulta) {
        List<Future<?>> enviadas = new ArrayList<>();
        try (ExecutorService ejecutor = Executors.newFixedThreadPool(peticiones)) {
            for (int i = 0; i < peticiones; i++) {
                enviadas.add(ejecutor.submit(consulta));
            }
        }
        for (Future<?> enviada : enviadas) {
            try {
                enviada.get();
            } catch (InterruptedException | ExecutionException fallo) {
                throw new IllegalStateException(fallo);
            }
        }
    }
}
