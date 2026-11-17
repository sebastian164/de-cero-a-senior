package nosql;

import static org.junit.jupiter.api.Assertions.*;

import comun.Arquitectura;
import comun.Docker;
import comun.Entorno;
import comun.Hilos;
import comun.Salida;
import io.lettuce.core.RedisClient;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import nosql.adaptadores.redis.CambiarPrecioInvalidando;
import nosql.adaptadores.redis.CatalogoConCache;
import nosql.adaptadores.redis.ClienteRedis;
import nosql.adaptadores.redis.PublicadorDeInvalidaciones;
import nosql.aplicacion.PoliticaDeReintento;
import nosql.aplicacion.VistaDeProducto;
import org.junit.jupiter.api.*;
import tienda.dominio.Dinero;
import tienda.dominio.EstadoDePedido;
import tienda.dominio.Pedido;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CacheTest {
    static final String R = "resultados.txt";
    static RedisClient cliente;
    static StatefulRedisConnection<String, String> conexion;
    static RedisCommands<String, String> redis;

    @BeforeAll
    static void preparar() {
        Salida.limpiar(R);
        cliente = ClienteRedis.conTiemposCortos(Entorno.redisUrl(), Duration.ofMillis(300));
        conexion = cliente.connect();
        redis = conexion.sync();
    }

    @BeforeEach void vaciar() { redis.flushall(); }
    @AfterAll static void cerrar() { redis.flushall(); cliente.shutdown(); }

    static BaseLenta base(int n, long latencia) {
        BaseLenta b = new BaseLenta(latencia);
        for (int i = 1; i <= n; i++) b.datos.put("P" + i, new VistaDeProducto("P" + i, 1000 + i, 100));
        return b;
    }

    static Composicion.Sistema mongo(String nombre) {
        return Composicion.elegir("mongo", Entorno.mongoUrl(), nombre + "_" + UUID.randomUUID().toString().substring(0, 6), PoliticaDeReintento.inmediata(3), Clock.systemUTC());
    }

    // ------------------------------------------------------------------ 1
    @Test @Order(1)
    void paso1_cacheAsideConAciertosYLatencia() {
        var s = mongo("t05_p1");
        try {
            for (int i = 1; i <= 1000; i++) s.siembra().producto("P" + i, "Producto " + i, 1000 + i, 100);
            var cache = new CatalogoConCache(s.productos(), conexion, CatalogoConCache.Config.basica(600_000));   // TTL largo: la prueba mide aciertos, no caducidad, y no debe depender de lo deprisa que vaya la máquina
            Random r = new Random(3);
            int n = 20_000;
            String[] ids = new String[n];
            for (int i = 0; i < n; i++) ids[i] = "P" + (1 + (int) (1000 * Math.pow(r.nextDouble(), 3)));
            long a = System.nanoTime();
            for (String id : ids) cache.porId(id);
            long conCache = (System.nanoTime() - a) / n / 1000;
            a = System.nanoTime();
            for (String id : ids) s.productos().porId(id);
            long sinCache = (System.nanoTime() - a) / n / 1000;
            double tasa = cache.aciertos.get() / (double) n;
            Salida.linea(R, "PASO 1. %d lecturas: aciertos %d (%.1f %%), lecturas a MongoDB %d; latencia media con caché %d us, directa a MongoDB %d us"
                    .formatted(n, cache.aciertos.get(), tasa * 100, cache.lecturasBase.get(), conCache, sinCache));
            assertTrue(tasa > 0.85);
            assertEquals(0, cache.erroresRedis.get());
            int sinCaducidad = 0, claves = 0;
            for (String k : redis.keys("pedidos:*")) { claves++; if (redis.pttl(k) == -1) sinCaducidad++; }   // -2 = caducó entre KEYS y PTTL
            Salida.linea(R, "  claves en Redis %d, sin caducidad %d, TTL de la primera %d ms".formatted(claves, sinCaducidad, redis.pttl(redis.keys("pedidos:*").get(0))));
            assertEquals(0, sinCaducidad);
            assertTrue(claves > 0);
        } finally { s.borrarYCerrar().run(); }
    }

    // ------------------------------------------------------------------ 2
    @Test @Order(2)
    void paso2_invalidacion() {
        var s = mongo("t05_p2");
        try {
            s.siembra().producto("P1", "Producto 1", 1000, 10);
            var cache = new CatalogoConCache(s.productos(), conexion, CatalogoConCache.Config.basica(3000));
            var entrada = new CambiarPrecioInvalidando(s.precios(), cache);
            cache.porId("P1");
            long a = System.nanoTime();
            entrada.cambiar("P1", new Dinero(1500));
            long precio = cache.porId("P1").orElseThrow().precioCentavos();
            Salida.linea(R, "PASO 2. (a) cambio por el puerto de entrada con borrado tras confirmar: se ve %d en %d ms".formatted(precio, (System.nanoTime() - a) / 1_000_000));
            assertEquals(1500, precio);

            cache.porId("P1");
            s.mongo().catalogo().cambiarPrecio("P1", new Dinero(2000));      // directo en la base: no pasa por el decorador ni genera evento
            long t0 = System.currentTimeMillis();
            while (cache.porId("P1").orElseThrow().precioCentavos() != 2000) { assertTrue(System.currentTimeMillis() - t0 < 10_000); Hilos.dormir(50); }
            long directo = System.currentTimeMillis() - t0;
            Salida.linea(R, "  (b) cambio directo en la base: se ve al cabo de %d ms (TTL 3000 ms con azar del 20 %%: máximo posible %d ms)".formatted(directo, cache.ttlMaximoMs()));
            assertTrue(directo <= cache.ttlMaximoMs() + 300);

            try (var pub = new PublicadorDeInvalidaciones(s.mongo().bandeja(), cache, 100)) {
                cache.porId("P1");
                s.precios().cambiar("P1", new Dinero(2500));         // otro proceso: usa el servicio, no el decorador
                t0 = System.currentTimeMillis();
                while (cache.porId("P1").orElseThrow().precioCentavos() != 2500) { assertTrue(System.currentTimeMillis() - t0 < 10_000); Hilos.dormir(10); }
                long eventos = System.currentTimeMillis() - t0;
                Salida.linea(R, "  (c) invalidación por eventos desde la bandeja de salida (sondeo cada 100 ms): se ve al cabo de %d ms".formatted(eventos));
                assertTrue(eventos < 1000);
            }
        } finally { s.borrarYCerrar().run(); }
    }

    // ------------------------------------------------------------------ 3
    /** Las "instancias" son decoradores distintos en este mismo proceso, con la misma Redis: lo que importa es que no comparten memoria entre ellas. */
    long estampida(BaseLenta b, CatalogoConCache.Carga modo, int instancias) {
        List<CatalogoConCache> cs = new ArrayList<>();
        for (int i = 0; i < instancias; i++) cs.add(new CatalogoConCache(b, conexion, CatalogoConCache.Config.basica(60_000).con(modo)));
        redis.flushall();
        b.lecturas.set(0);
        Hilos.aLaVez(200, i -> () -> cs.get(i % instancias).porId("P1"));
        return b.lecturas.get();
    }

    @Test @Order(3)
    void paso3_estampida() {
        BaseLenta b = base(10, 100);
        long sin = estampida(b, CatalogoConCache.Carga.NINGUNA, 1);
        long unica = estampida(b, CatalogoConCache.Carga.UNICA_POR_PROCESO, 1);
        long tres = estampida(b, CatalogoConCache.Carga.UNICA_POR_PROCESO, 3);
        long cerrojo = estampida(b, CatalogoConCache.Carga.CERROJO_DE_RECARGA, 3);
        Salida.linea(R, "PASO 3. 200 peticiones a la vez sobre una clave caducada (la base tarda 100 ms; \"instancia\" = un decorador con su propia memoria, en el mismo proceso): lecturas a la base");
        Salida.linea(R, "  sin protección: %d; carga única por proceso: %d; tres instancias con carga única: %d; tres instancias con cerrojo de recarga: %d".formatted(sin, unica, tres, cerrojo));
        assertTrue(sin >= 100);
        assertEquals(1, unica);
        assertTrue(tres <= 3 && tres >= 1);
        assertEquals(1, cerrojo);
    }

    @Test @Order(3)
    void paso3b_siLaCargaUnicaFallaLosQueEsperanNoHeredanElError() {
        BaseLenta b = base(10, 100);
        java.util.concurrent.atomic.AtomicInteger llamadas = new java.util.concurrent.atomic.AtomicInteger();
        var falla = new nosql.aplicacion.ConsultarProducto() {
            public Optional<VistaDeProducto> porId(String id) {
                if (llamadas.incrementAndGet() == 1) { Hilos.dormir(150); throw new IllegalStateException("la primera carga falla"); }
                return b.porId(id);
            }
        };
        var c = new CatalogoConCache(falla, conexion, CatalogoConCache.Config.basica(60_000).con(CatalogoConCache.Carga.UNICA_POR_PROCESO));
        var resultados = Hilos.aLaVez(20, i -> () -> { try { return c.porId("P1").isPresent() ? "ok" : "vacío"; } catch (IllegalStateException e) { return "error"; } });
        long errores = resultados.stream().filter("error"::equals).count();
        Salida.linea(R, "PASO 3b. la carga única falla: de 20 peticiones, 1 recibe el error (la que cargaba) y %d leen de la base y responden".formatted(resultados.stream().filter("ok"::equals).count()));
        assertEquals(1, errores);
    }

    // ------------------------------------------------------------------ 4
    @Test @Order(4)
    void paso4_penetracion() {
        int existentes = 10_000, peticiones = 4000;
        BaseLenta b = base(existentes, 0);
        Random r = new Random(9);
        String[] repetidos = new String[peticiones], unicos = new String[peticiones];
        for (int i = 0; i < peticiones; i++) { repetidos[i] = "X" + r.nextInt(500); unicos[i] = "Y" + r.nextLong(); }
        Salida.linea(R, "PASO 4. %d peticiones a identificadores que no existen; lecturas a la base:".formatted(peticiones));
        record Fila(String nombre, long repetidos, long unicos) {}
        List<Fila> filas = new ArrayList<>();
        long memNegativas = 0;
        for (String modo : List.of("sin protección", "caché negativa", "filtro de Bloom")) {
            long[] lecturas = new long[2];
            for (int k = 0; k < 2; k++) {
                redis.flushall();
                var cfg = CatalogoConCache.Config.basica(60_000);
                if (modo.equals("sin protección")) cfg = cfg.sinNegativa();
                if (modo.equals("filtro de Bloom")) cfg = cfg.conBloom("pedidos:catalogo:v1:bloom");
                var c = new CatalogoConCache(b, conexion, cfg);
                if (modo.equals("filtro de Bloom")) {
                    c.crearBloom(0.01, existentes);
                    c.agregarABloom(b.datos.keySet());
                }
                b.lecturas.set(0);
                for (String id : k == 0 ? repetidos : unicos) c.porId(id);
                lecturas[k] = b.lecturas.get();
                if (modo.equals("caché negativa") && k == 1) memNegativas = memoriaDeClaves(CatalogoConCache.PREFIJO + "Y*");
            }
            filas.add(new Fila(modo, lecturas[0], lecturas[1]));
            Salida.linea(R, "  %-16s ids repetidos (500 distintos): %4d   ids al azar todos distintos: %4d".formatted(modo, lecturas[0], lecturas[1]));
        }
        assertEquals(peticiones, filas.get(0).repetidos());
        assertTrue(filas.get(1).repetidos() <= 500);
        assertEquals(peticiones, filas.get(1).unicos(), "la caché negativa no ayuda con identificadores siempre nuevos");
        assertTrue(filas.get(2).unicos() < peticiones * 0.03, "el filtro de Bloom deja pasar solo los falsos positivos (~1 %)");
        for (String id : b.datos.keySet()) redis.sadd("pedidos:catalogo:v1:conjunto-ids", id);
        long memBloom = (Long) redis.memoryUsage("pedidos:catalogo:v1:bloom"), memSet = (Long) redis.memoryUsage("pedidos:catalogo:v1:conjunto-ids");
        Salida.linea(R, "  MEMORY USAGE con %d identificadores: filtro de Bloom %d bytes, conjunto (SET) %d bytes".formatted(existentes, memBloom, memSet));
        Salida.linea(R, "  MEMORY USAGE de las %d claves negativas que deja el ataque de ids siempre nuevos: %d bytes (el filtro ocupa %d y no crece con el ataque)".formatted(peticiones, memNegativas, memBloom));
        assertTrue(memBloom < memSet);
        assertTrue(memNegativas > memBloom, "la caché negativa llena Redis con basura del atacante; el filtro, no");
    }

    static long memoriaDeClaves(String patron) {
        long total = 0;
        ScanCursor cursor = ScanCursor.INITIAL;
        do {
            var r = redis.scan(cursor, ScanArgs.Builder.matches(patron).limit(500));
            for (String k : r.getKeys()) total += Optional.ofNullable(redis.memoryUsage(k)).orElse(0L);
            cursor = r;
        } while (!cursor.isFinished());
        return total;
    }

    // ------------------------------------------------------------------ 5
    @Test @Order(5)
    void paso5_redisCaido() {
        BaseLenta b = base(100, 3);
        RedisClient porDefecto = ClienteRedis.porDefecto(Entorno.redisUrl());
        var conexionPorDefecto = porDefecto.connect();
        var conDefecto = new CatalogoConCache(b, conexionPorDefecto, CatalogoConCache.Config.basica(60_000));
        var corto = new CatalogoConCache(b, conexion, CatalogoConCache.Config.basica(60_000));
        conDefecto.porId("P1"); corto.porId("P1");
        // (a) Redis colgado (pausado: el socket sigue abierto y nadie contesta). Es lo que más daño hace: no hay error, hay silencio.
        Docker.pausar("redis");
        try {
            CompletableFuture<Object> bloqueada = CompletableFuture.supplyAsync(() -> conDefecto.porId("P2"));
            boolean sigueBloqueada;
            try { bloqueada.get(8, TimeUnit.SECONDS); sigueBloqueada = false; } catch (TimeoutException e) { sigueBloqueada = true; } catch (Exception e) { sigueBloqueada = false; }
            Salida.linea(R, "PASO 5. Redis colgado (docker pause), cliente con valores por defecto: una lectura " + (sigueBloqueada ? "sigue sin responder tras 8 s (el tiempo de espera de Lettuce es de 60 s)" : "respondió"));
            assertTrue(sigueBloqueada);
            long a0 = System.nanoTime();
            assertTrue(corto.porId("P3").isPresent());
            Salida.linea(R, "  Redis colgado, tiempos cortos (300 ms): la lectura responde desde la base en %d ms (el tiempo de espera, más la base)".formatted((System.nanoTime() - a0) / 1_000_000));
        } finally {
            Docker.reanudar("redis");
        }
        Hilos.dormir(500);
        // (b) Redis caído (detenido)
        Docker.parar("redis");
        try {
            porDefecto.shutdown(0, 0, TimeUnit.SECONDS);
            long[] t = new long[300];
            for (int i = 0; i < t.length; i++) { long a = System.nanoTime(); assertTrue(corto.porId("P" + (1 + i % 100)).isPresent()); t[i] = (System.nanoTime() - a) / 1000; }
            Salida.linea(R, "  (b) Redis detenido (docker stop), tiempos cortos (300 ms) y a la base si falla: 300 lecturas respondidas, media %.0f us, p95 %d us, errores de Redis %d (la base tarda 3 ms)"
                    .formatted(Salida.media(t), Salida.percentil(t, 0.95), corto.erroresRedis.get()));
            assertTrue(corto.erroresRedis.get() >= 300);
        } finally {
            Docker.arrancar("redis");
            for (int i = 0; i < 100; i++) { try { redis.ping(); break; } catch (RuntimeException e) { Hilos.dormir(200); } }   // que el resto de pruebas encuentre la conexión
        }
        long t0 = System.currentTimeMillis();
        long antes = corto.aciertos.get();
        while (corto.aciertos.get() == antes) {
            assertTrue(System.currentTimeMillis() - t0 < 40_000, "la caché debe recuperarse sola");
            corto.porId("P1"); corto.porId("P1"); Hilos.dormir(200);
        }
        Salida.linea(R, "  Redis de vuelta: la caché vuelve a dar aciertos tras %d ms, sin reiniciar el servicio".formatted(System.currentTimeMillis() - t0));
    }

    // ------------------------------------------------------------------ 6
    @Test @Order(6)
    void paso6_desalojo() {
        redis.configSet("maxmemory", "5mb");
        redis.configSet("maxmemory-policy", "allkeys-lru");
        try {
            BaseLenta b = new BaseLenta(0);
            int n = 60_000;
            for (int i = 1; i <= n; i++) b.datos.put("P" + i, new VistaDeProducto("P" + i, 1000, 1));
            var c = new CatalogoConCache(b, conexion, CatalogoConCache.Config.basica(600_000));
            for (int i = 1; i <= n; i++) c.porId("P" + i);
            assertEquals(0, c.erroresRedis.get(), "con allkeys-lru Redis desaloja y no rechaza escrituras: ningún error de Redis durante el llenado");
            long evicted = Long.parseLong(redis.info("stats").lines().filter(l -> l.startsWith("evicted_keys:")).findFirst().orElseThrow().split(":")[1].trim());
            long claves = redis.dbsize();
            Random r = new Random(5);
            c.aciertos.set(0); c.fallos.set(0);
            for (int i = 0; i < 20_000; i++) c.porId("P" + (1 + (int) (n * Math.pow(r.nextDouble(), 3))));
            double tasa = c.aciertos.get() / 20_000.0;
            Salida.linea(R, "PASO 6. maxmemory 5mb, allkeys-lru, %d productos cargados (errores de Redis durante el llenado: %d): evicted_keys %d, claves que quedan %d de %d; tasa de aciertos con lectura sesgada %.1f %%"
                    .formatted(n, c.erroresRedis.get(), evicted, claves, n, tasa * 100));
            assertTrue(evicted > 0);
            assertTrue(claves < n);
        } finally {
            redis.configSet("maxmemory", "0");
            redis.configSet("maxmemory-policy", "noeviction");
        }
    }

    // ------------------------------------------------------------------ 7
    @Test @Order(7)
    void paso7_cacheDelanteDeLosPedidosEsUnError() throws Exception {
        var s = mongo("t05_p7");
        try {
            s.siembra().producto("A", "A", 100, 100);
            String id = s.crear().crear(Pruebas.solicitud("k1", "c1", "A:1")).pedido().id();
            var conCache = new PedidosConCache(s.mongo().pedidos(), redis);
            CountDownLatch lecturaHecha = new CountDownLatch(1), puedeGuardar = new CountDownLatch(1);
            conCache.entreLeerYGuardar = () -> { lecturaHecha.countDown(); try { puedeGuardar.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } };
            CompletableFuture<Pedido> lector = CompletableFuture.supplyAsync(() -> conCache.buscar(id).orElseThrow());   // lee PENDIENTE de la base
            lecturaHecha.await();
            s.confirmar().confirmar(id);                             // escritura concurrente...
            conCache.invalidar(id);                                  // ...y su invalidación, que llega antes de que el lector guarde
            conCache.entreLeerYGuardar = () -> {};
            puedeGuardar.countDown();
            lector.get();
            Pedido enCache = conCache.buscar(id).orElseThrow();
            Pedido enBase = s.mongo().pedidos().buscar(id).orElseThrow();
            Salida.linea(R, "PASO 7. caché delante del repositorio de pedidos: tras confirmar, la base dice %s y la caché %s".formatted(enBase.estado(), enCache.estado()));
            assertEquals(EstadoDePedido.CONFIRMADO, enBase.estado());
            assertEquals(EstadoDePedido.PENDIENTE, enCache.estado());
            assertTrue(redis.ttl("pedidos:pedido:v1:" + id) > 0);
        } finally { s.borrarYCerrar().run(); }
    }

    // ------------------------------------------------------------------ arquitectura
    @Test @Order(8)
    void elNucleoNoConoceRedisYLaEscrituraNoRecibeLaCache() throws Exception {
        List<Path> fuentes = new ArrayList<>(Arquitectura.fuentes(Path.of("../../../04-mongodb-en-el-banco-hexagonal/taller/solucion-de-referencia/src/main/java")));
        fuentes.addAll(Arquitectura.fuentes(Path.of("../../../../hexagonal/08-pruebas-y-limites/taller/solucion-de-referencia/src")));
        fuentes.addAll(Arquitectura.fuentes(Path.of("src/main/java")));
        assertEquals(List.of(), Arquitectura.violaciones(fuentes));
        Salida.linea(R, "ARQUITECTURA. %d archivos (núcleo, aplicación y adaptadores de los módulos 04 y 05): io.lettuce, com.mongodb y org.bson solo aparecen bajo adaptadores/ y en la composición; los casos de uso no importan ninguno".formatted(fuentes.size()));
        // ningún caso de uso de escritura recibe el decorador de la caché
        for (Class<?> uso : List.of(tienda.aplicacion.ServicioDePedidos.class, nosql.aplicacion.ServicioDePedidosConEspera.class, nosql.aplicacion.ServicioDeCatalogo.class,
                nosql.aplicacion.CrearPedidoAcotado.class)) {
            for (var ctor : uso.getConstructors()) for (Class<?> tipo : ctor.getParameterTypes()) {
                assertNotEquals(nosql.aplicacion.ConsultarProducto.class, tipo, uso.getSimpleName() + " no puede depender de ConsultarProducto (la caché)");
            }
        }
    }
}
