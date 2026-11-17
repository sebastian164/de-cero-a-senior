package integrador;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.resource.Delay;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import nosql.Composicion;
import nosql.adaptadores.redis.CatalogoConCache;
import nosql.adaptadores.redis.CambiarPrecioInvalidando;
import nosql.aplicacion.PoliticaDeReintento;
import nosql.aplicacion.TurnoCaducado;
import nosql.proteccion.Composicion.Config;
import org.bson.Document;

/**
 * Una instancia del servicio de pedidos: la raíz de composición. Une el sistema sobre MongoDB (sesión 04), la caché del catálogo
 * (sesión 05), la protección con Redis (sesión 06), el publicador de la bandeja y la tarea de cancelación con cerrojo y turno.
 * Se configura con variables de entorno, para que el mismo artefacto corra en el contenedor y fuera de él.
 */
public final class Servidor {
    static String env(String k, String def) { return System.getenv().getOrDefault(k, def); }

    public static void main(String[] args) throws Exception {
        int puerto = Integer.parseInt(env("PORT", "8080"));
        String instancia = env("INSTANCIA", "i" + puerto), mongoUrl = env("MONGO_URL", "mongodb://localhost:27017/?directConnection=true"), redisUrl = env("REDIS_URL", "redis://localhost:6379");
        String bd = env("DB", "t07");
        long ttl = Long.parseLong(env("TTL_PRECIO_MS", "4000")), umbralMs = Long.parseLong(env("UMBRAL_CANCELACION_MS", "1800000")), vidaCerrojo = Long.parseLong(env("VIDA_CERROJO_MS", "3000"));
        int limite = Integer.parseInt(env("LIMITE", "20"));
        long ventana = Long.parseLong(env("VENTANA_MS", "10000"));

        AtomicLong reintentos = new AtomicLong();
        PoliticaDeReintento espera = PoliticaDeReintento.esperaCreciente(50, 10, 1000);
        PoliticaDeReintento contada = new PoliticaDeReintento() {
            public int maximoDeIntentos() { return espera.maximoDeIntentos(); }
            public void esperar(int i) { reintentos.incrementAndGet(); espera.esperar(i); }
        };
        var sistema = Composicion.mongo(mongoUrl, bd, contada, Clock.systemUTC());
        var almacen = sistema.mongo();

        // Lettuce con tiempos cortos y reconexión acotada: tras volver Redis, la instancia lo ve en menos de un segundo, no en decenas
        ClientResources recursos = DefaultClientResources.builder().reconnectDelay(Delay.constant(Duration.ofMillis(500))).build();
        RedisClient redisClient = RedisClient.create(recursos, redisUrl);
        Duration espera300 = Duration.ofMillis(300);
        redisClient.setOptions(ClientOptions.builder().socketOptions(SocketOptions.builder().connectTimeout(espera300).build())
                .timeoutOptions(TimeoutOptions.enabled(espera300)).disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS).build());
        var conexion = redisClient.connect();
        var redis = conexion.sync();

        var cache = new CatalogoConCache(sistema.productos(), conexion, new CatalogoConCache.Config(ttl, 0.2, 30_000, CatalogoConCache.Carga.UNICA_POR_PROCESO, null));
        var precios = new CambiarPrecioInvalidando(sistema.precios(), cache);
        AtomicLong publicados = new AtomicLong(), canceladosPorTarea = new AtomicLong(), turnosRechazados = new AtomicLong(), conflictosDeLaTarea = new AtomicLong();
        AtomicBoolean tieneCerrojo = new AtomicBoolean();

        java.util.concurrent.atomic.AtomicReference<nosql.proteccion.Composicion.Grafo> referencia = new java.util.concurrent.atomic.AtomicReference<>();
        var grafo = nosql.proteccion.Composicion.montar(sistema, cache, redis, puerto, Config.por(limite, ventana).conEnCurso(Long.parseLong(env("EN_CURSO_MS", "2000"))), () -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("instancia", instancia);
            m.put("cacheAciertos", cache.aciertos.get()); m.put("cacheFallos", cache.fallos.get()); m.put("lecturasBase", cache.lecturasBase.get());
            m.put("erroresRedis", cache.erroresRedis.get()); m.put("reintentosPorConflicto", reintentos.get());
            m.put("eventosPublicados", publicados.get()); m.put("canceladosPorTarea", canceladosPorTarea.get()); m.put("turnosRechazados", turnosRechazados.get());
            m.put("conflictosDeLaTarea", conflictosDeLaTarea.get()); m.put("tieneCerrojo", tieneCerrojo.get());
            var g = referencia.get();
            m.put("idempotenciaDegradada", g == null ? 0L : g.idempotencia().degradadas.get());
            m.put("limiteRedisCaido", g == null ? 0L : g.limite().redisCaido.get());
            try { m.put("eventosPendientes", almacen.bandeja().contarPendientes()); } catch (RuntimeException e) { m.put("eventosPendientes", -1L); }
            return m;
        });
        referencia.set(grafo);
        var control = almacen.db().getCollection("control");

        Thread publicador = new Thread(() -> {
            while (true) {
                try {
                    boolean pausado = control.find(new Document("_id", "publicador")).first() != null && control.find(new Document("_id", "publicador")).first().getBoolean("pausado", false);
                    if (!pausado) {
                        for (var e : almacen.bandeja().pendientes(200)) {
                            if (e.tipo().equals("PrecioCambiado")) cache.invalidar(e.pedidoId());     // en este evento, la referencia es el producto
                            almacen.bandeja().marcarPublicado(e.id());
                            publicados.incrementAndGet();
                        }
                    }
                    Thread.sleep(200);
                } catch (InterruptedException x) { return; } catch (RuntimeException x) { /* se reintenta */ }
            }
        }, "publicador");
        publicador.setDaemon(true);
        publicador.start();

        Thread tarea = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(500);
                    var c = grafo.cerrojo().adquirir("cancelar-abandonados", vidaCerrojo);
                    if (c.isEmpty()) continue;
                    tieneCerrojo.set(true);
                    try {
                        var r = grafo.cancelarAbandonados().cancelar("cancelar-abandonados", c.get().turno(), Instant.now().minusMillis(umbralMs), 200);
                        canceladosPorTarea.addAndGet(r.canceladas());
                        conflictosDeLaTarea.addAndGet(r.enConflicto());
                    } catch (TurnoCaducado e) {
                        turnosRechazados.incrementAndGet();
                    } finally { tieneCerrojo.set(false); grafo.cerrojo().liberar(c.get()); }
                } catch (InterruptedException x) { return; } catch (RuntimeException x) { tieneCerrojo.set(false); }
            }
        }, "cancelacion");
        tarea.setDaemon(true);
        tarea.start();

        System.out.println("LISTO " + instancia);
        Thread.currentThread().join();
    }
}
