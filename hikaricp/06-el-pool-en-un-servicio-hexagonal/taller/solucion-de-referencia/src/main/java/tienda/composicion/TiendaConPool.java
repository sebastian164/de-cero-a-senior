package tienda.composicion;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import tienda.adaptadores.entrada.carga.ServidorConcurrente;
import tienda.adaptadores.salida.http.NotificadorHttp;
import tienda.adaptadores.salida.jdbc.BandejaJdbc;
import tienda.adaptadores.salida.jdbc.CatalogoJdbc;
import tienda.adaptadores.salida.jdbc.InventarioJdbc;
import tienda.adaptadores.salida.jdbc.PedidosJdbc;
import tienda.adaptadores.salida.jdbc.PeticionesJdbc;
import tienda.adaptadores.salida.jdbc.UnidadDeTrabajoJdbc;
import tienda.adaptadores.salida.sistema.RelojDelSistema;
import tienda.aplicacion.EventoDePedido;
import tienda.aplicacion.ServicioDePedidos;
import tienda.aplicacion.puertos.salida.PublicadorDeEventos;

/**
 * La raíz de composición del taller 06: el único sitio que crea el pool. Variables de entorno:
 *   DB_URL, DB_USUARIO, DB_PASSWORD, POOL (tamaño), PUERTO, NOTIFICACIONES (url del servicio lento),
 *   EVENTOS = directo (paso 6, el defecto) | bandeja (paso 8, el arreglo) | ninguno (paso 5, sin servicio lento), FUGAS_MS (leakDetectionThreshold, 0 = no)
 */
public final class TiendaConPool {

    private TiendaConPool() {
    }

    public static void main(String[] args) throws Exception {
        HikariConfig configuracion = new HikariConfig();
        configuracion.setPoolName("tienda");
        configuracion.setJdbcUrl(System.getenv("DB_URL"));
        configuracion.setUsername(System.getenv("DB_USUARIO"));
        configuracion.setPassword(System.getenv("DB_PASSWORD"));
        configuracion.setMaximumPoolSize(Integer.parseInt(System.getenv("POOL")));   // sale de la cuenta del paso 5
        configuracion.setConnectionTimeout(1_000);              // no cambia entre el defecto y el arreglo
        configuracion.setLeakDetectionThreshold(Long.parseLong(System.getenv().getOrDefault("FUGAS_MS", "0")));
        SimpleMeterRegistry metricas = new SimpleMeterRegistry();
        configuracion.setMetricRegistry(metricas);              // hikaricp.connections.usage, acquire, timeout...
        HikariDataSource pool = new HikariDataSource(configuracion);

        UnidadDeTrabajoJdbc transacciones = new UnidadDeTrabajoJdbc(pool);
        NotificadorHttp notificador = new NotificadorHttp(System.getenv("NOTIFICACIONES"));
        BandejaJdbc bandeja = new BandejaJdbc(transacciones);
        boolean conBandeja = "bandeja".equals(System.getenv("EVENTOS"));
        boolean sinEventos = "ninguno".equals(System.getenv("EVENTOS"));
        PublicadorDeEventos eventos = sinEventos ? evento -> { } : conBandeja ? bandeja : notificador;
        ServicioDePedidos servicio = new ServicioDePedidos(new PedidosJdbc(transacciones), new CatalogoJdbc(transacciones),
                new InventarioJdbc(transacciones), new PeticionesJdbc(transacciones), transacciones, new RelojDelSistema(), eventos);

        AtomicLong entregados = new AtomicLong();
        AtomicLong fallidos = new AtomicLong();
        if (conBandeja) {
            // El mensajero: entrega lotes de la bandeja, cada evento en su hilo virtual, sin conexión mientras espera.
            // Solo cuenta (y deja borrar) lo que el servicio recibió bien; lo que falla se queda en la bandeja.
            Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(() -> {
                try {
                    bandeja.entregarPendientes(500, lote -> entregar(lote, notificador, entregados, fallidos));
                } catch (RuntimeException fallo) {
                    System.err.println("entrega pendiente: " + fallo.getMessage());
                }
            }, 100, 100, TimeUnit.MILLISECONDS);
        }

        HikariPoolMXBean estado = pool.getHikariPoolMXBean();
        ServidorConcurrente servidor = new ServidorConcurrente(Integer.parseInt(System.getenv("PUERTO")), servicio, () -> {
            Timer uso = metricas.find("hikaricp.connections.usage").timer();
            Timer espera = metricas.find("hikaricp.connections.acquire").timer();
            double timeouts = metricas.find("hikaricp.connections.timeout").counter() == null ? 0
                    : metricas.find("hikaricp.connections.timeout").counter().count();
            return String.format(Locale.ROOT, "{\"max\":%d,\"activas\":%d,\"pendientes\":%d,\"usoMedioMs\":%.2f,\"usoMaxMs\":%.1f,\"esperaMediaMs\":%.2f,\"timeouts\":%d,\"entregados\":%d,\"fallidos\":%d}",
                    pool.getMaximumPoolSize(), estado.getActiveConnections(), estado.getThreadsAwaitingConnection(),
                            uso == null ? 0 : uso.mean(TimeUnit.MILLISECONDS), uso == null ? 0 : uso.max(TimeUnit.MILLISECONDS),
                            espera == null ? 0 : espera.mean(TimeUnit.MILLISECONDS), (long) timeouts, entregados.get(), fallidos.get());
        });
        servidor.iniciar();
        System.out.println("tienda con pool de " + pool.getMaximumPoolSize() + " en " + System.getenv("PUERTO")
                + ", eventos " + (conBandeja ? "por la bandeja de salida" : sinEventos ? "ninguno" : "directos al servicio lento"));
    }

    /** Entrega cada evento en su hilo virtual y devuelve los ids de los que el servicio recibió bien. */
    public static Set<String> entregar(List<EventoDePedido> lote, PublicadorDeEventos destino, AtomicLong entregados, AtomicLong fallidos) {
        Set<String> bien = new HashSet<>();
        try (var hilos = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> intentos = new ArrayList<>();
            for (EventoDePedido evento : lote) {
                intentos.add(hilos.submit(() -> destino.publicar(evento)));
            }
            for (int i = 0; i < lote.size(); i++) {
                try {
                    intentos.get(i).get();
                    bien.add(lote.get(i).id());
                } catch (java.util.concurrent.ExecutionException error) {
                    fallidos.incrementAndGet();                  // se queda en la bandeja para la vuelta siguiente
                } catch (InterruptedException interrupcion) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        entregados.addAndGet(bien.size());
        return bien;
    }
}
