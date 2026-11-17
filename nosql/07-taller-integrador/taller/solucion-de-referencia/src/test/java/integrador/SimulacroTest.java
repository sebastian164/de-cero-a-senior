package integrador;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;
import comun.Compose;
import comun.Entorno;
import comun.Hilos;
import comun.Salida;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.sync.RedisCommands;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import nosql.Composicion;
import nosql.adaptadores.mongo.VerificadorDeInvariantes;
import org.bson.Document;
import org.junit.jupiter.api.*;

/**
 * El simulacro del taller integrador. Todo el sistema se levanta con UNA orden de Compose (MongoDB, Redis y dos instancias del servicio en
 * contenedores), con una carga de fondo continua. Después de cada prueba corre el verificador de invariantes.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SimulacroTest {
    static final String R = "simulacro.txt", BD = "t07";
    static final int PA = 18081, PB = 18082;
    static final String MONGO = "mongodb://localhost:28017/?directConnection=true", REDIS = "redis://localhost:26379";
    static MongoClient mongo;
    static Composicion.Sistema sistema;
    static RedisClient redisClient;
    static RedisCommands<String, String> redis;
    static Compose compose;
    static GeneradorDeCarga carga;
    static HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    static AtomicInteger seq = new AtomicInteger();

    // ---------------------------------------------------------------- infraestructura de la prueba
    static void levantar(Map<String, String> entorno) {
        compose = new Compose(System.getProperty("infra.proyecto", "nosql") + "-integrador", Path.of("compose.yaml").toAbsolutePath().toString(), entorno);
        compose.arriba();
        for (int puerto : new int[]{PA, PB}) esperarInstancia(puerto);
    }

    static void esperarInstancia(int puerto) {
        for (int i = 0; i < 120; i++) {
            try { if (get(puerto, "/metricas").statusCode() == 200) return; } catch (Exception e) { /* aún no */ }
            Hilos.dormir(500);
        }
        throw new IllegalStateException("la instancia " + puerto + " no arrancó");
    }

    static HttpResponse<String> get(int puerto, String ruta) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    static HttpResponse<String> pedir(int puerto, String cliente, String clave, String productos) throws Exception {
        StringBuilder lineas = new StringBuilder();
        String[] ps = productos.split(",");
        for (int i = 0; i < ps.length; i++) lineas.append(i > 0 ? "," : "").append("{\"producto\":\"").append(ps[i]).append("\",\"cantidad\":1}");
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/pedidos")).timeout(Duration.ofSeconds(30)).header("Idempotency-Key", clave)
                .POST(HttpRequest.BodyPublishers.ofString("{\"cliente\":\"" + cliente + "\",\"lineas\":[" + lineas + "]}")).build(), HttpResponse.BodyHandlers.ofString());
    }

    static Document metricas(int puerto) throws Exception { return Document.parse(get(puerto, "/metricas").body()); }
    static long metrica(int puerto, String campo) throws Exception { return metricas(puerto).get(campo, Number.class).longValue(); }
    static long suma(String campo) throws Exception { return metrica(PA, campo) + metrica(PB, campo); }

    static void sembrar(String id, int existencias, long precio) {
        sistema.siembra().producto(id, id, precio, existencias);
        mongo.getDatabase(BD).getCollection("iniciales").replaceOne(Filters.eq("_id", id), new Document("_id", id).append("existencias", existencias), new com.mongodb.client.model.ReplaceOptions().upsert(true));
    }

    static void verificar(String prueba, String resultado) {
        carga.pausar();                                             // los invariantes se comprueban con el sistema quieto: sin peticiones en vuelo
        List<String> fallos = VerificadorDeInvariantes.verificar(mongo, BD);
        Salida.linea(R, "| %-62s | %-90s | %s |".formatted(prueba, resultado, fallos.isEmpty() ? "verificador: OK" : "verificador: FALLA " + fallos.subList(0, Math.min(3, fallos.size()))));
        assertTrue(fallos.isEmpty(), fallos.toString());
    }

    static void volcarMetricasSiFalla(Runnable prueba) {
        try { prueba.run(); }
        catch (Throwable t) {
            try { Salida.linea("metricas-al-fallar.txt", Instant.now() + " " + t + "\n  a: " + metricas(PA).toJson() + "\n  b: " + metricas(PB).toJson() + "\n  " + carga.resumen()); } catch (Exception e) { /* nada */ }
            throw t;
        }
    }

    @BeforeAll
    static void preparar() throws Exception {
        Salida.limpiar(R); Salida.limpiar("metricas-al-fallar.txt");
        // desde cero: una sola orden levanta MongoDB, Redis y las dos instancias (se construye la imagen)
        new Compose(System.getProperty("infra.proyecto", "nosql") + "-integrador", Path.of("compose.yaml").toAbsolutePath().toString(), Map.of()).abajo();
        long t0 = System.currentTimeMillis();
        levantar(Map.of());
        long arranque = System.currentTimeMillis() - t0;
        mongo = com.mongodb.client.MongoClients.create(MONGO);
        sistema = Composicion.mongo(MONGO, BD, null, Clock.systemUTC());
        redisClient = RedisClient.create(REDIS);
        redis = redisClient.connect().sync();
        for (int i = 1; i <= 100; i++) sembrar("P" + i, 1_000_000, 1000 + i);
        for (int i = 1; i <= 20; i++) sembrar("G" + i, 10_000_000, 500 + i);
        carga = new GeneradorDeCarga(new int[]{PA, PB}, 2);
        Salida.linea(R, "Sistema levantado con `docker compose up -d --build --wait` en %d ms: mongo, redis, servicio-a (:%d) y servicio-b (:%d)".formatted(arranque, PA, PB));
        Salida.linea(R, "");
        Salida.linea(R, "| Prueba | Resultado | Verificador de invariantes |");
        Salida.linea(R, "|---|---|---|");
    }

    @AfterAll
    static void cerrar() throws Exception {
        carga.close();
        Salida.linea(R, "");
        Salida.linea(R, carga.resumen());
        try { Salida.linea("logs-servicio-a.txt", compose.logs("servicio-a")); Salida.linea("logs-servicio-b.txt", compose.logs("servicio-b")); } catch (RuntimeException e) { /* el contenedor puede no existir */ }
        compose.abajo();
        mongo.close(); redisClient.shutdown();
    }

    /** Antes de cada prueba, las dos instancias vivas y Redis respondiendo. */
    @BeforeEach
    void todoVivo() throws Exception {
        carga.reanudar();
        for (int puerto : new int[]{PA, PB}) esperarInstancia(puerto);
    }

    // ---------------------------------------------------------------- las pruebas
    @Test @Order(1)
    void s1_veinteCompradoresPorCincoUnidades_diezVeces() {
        volcarMetricasSiFalla(() -> {
            try {
                for (int rep = 1; rep <= 10; rep++) {
                    String prod = "E" + rep;
                    sembrar(prod, 5, 2000);
                    int r = rep;
                    List<HttpResponse<String>> res = Hilos.aLaVez(20, i -> () -> pedir(i % 2 == 0 ? PA : PB, "comp-" + r + "-" + i, "s1-" + r + "-" + i, prod));
                    long ok = res.stream().filter(x -> x.statusCode() == 201).count(), rechazados = res.stream().filter(x -> x.statusCode() == 422).count();
                    int stock = mongo.getDatabase(BD).getCollection("productos").find(Filters.eq("_id", prod)).first().getInteger("existencias");
                    assertEquals(5, ok, "repetición " + rep);
                    assertEquals(15, rechazados, "repetición " + rep);
                    assertEquals(0, stock);
                }
                verificar("20 compradores, 5 unidades (x10 repeticiones)", "cada repetición: 5 pedidos 201, 15 rechazos 422, existencias 0");
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    @Test @Order(2)
    void s2_cadaPostTresVeces() {
        volcarMetricasSiFalla(() -> {
            Hilos.aLaVez(30, i -> () -> {
                Set<String> ids = new HashSet<>();
                for (int k = 0; k < 3; k++) {
                    var r = pedir(k % 2 == 0 ? PA : PB, "s2-" + i, "s2-clave-" + i, "P1,P2");
                    assertEquals(201, r.statusCode());
                    ids.add(Document.parse(r.body()).getString("id"));
                }
                assertEquals(1, ids.size());
                return null;
            });
            long pedidos = mongo.getDatabase(BD).getCollection("peticiones").countDocuments(Filters.regex("_id", "^s2-clave-"));
            long cobros = mongo.getDatabase(BD + "_pagos").getCollection("cobros").countDocuments(Filters.regex("_id", "^s2-clave-"));
            assertEquals(30, pedidos);
            assertEquals(30, cobros);
            verificar("30 POST enviados 3 veces con la misma clave (90 envíos)", "%d pedidos, %d cobros, 0 duplicados".formatted(pedidos, cobros));
        });
    }

    @Test @Order(3)
    void s3_matarUnaInstanciaAMitadDeCrearPedido() throws Exception {
        volcarMetricasSiFalla(() -> {
            try {
                int n = 60;
                AtomicInteger respondidos = new AtomicInteger(), cortados = new AtomicInteger();
                Thread asesino = new Thread(() -> { Hilos.dormir(250); compose.matar("servicio-b"); });          // docker kill -s SIGKILL
                asesino.start();
                Hilos.aLaVez(n, i -> () -> {
                    try { var r = pedir(PB, "s3-" + i, "s3-clave-" + i, "P3,P4,P5,P6,P7,P8"); if (r.statusCode() == 201) respondidos.incrementAndGet(); }
                    catch (Exception e) { cortados.incrementAndGet(); }
                    return null;
                });
                asesino.join();
                // los clientes reintentan con la misma clave contra la instancia que sigue viva; las transacciones que dejó abiertas el contenedor muerto
                // retienen sus documentos hasta que MongoDB las aborta (transactionLifetimeLimitSeconds)
                long inicio = System.currentTimeMillis(), limite = inicio + 150_000;
                int intermedias = 0, exitos = 0;
                for (int i = 0; i < n; i++) {
                    while (true) {
                        HttpResponse<String> r;
                        try { r = pedir(PA, "s3-" + i, "s3-clave-" + i, "P3,P4,P5,P6,P7,P8"); }
                        catch (java.net.http.HttpTimeoutException e) { intermedias++; assertTrue(System.currentTimeMillis() < limite); continue; }
                        if (r.statusCode() == 201 || r.statusCode() == 200) { exitos++; break; }
                        assertTrue(r.statusCode() == 409 || r.statusCode() == 503, r.body());
                        intermedias++;
                        assertTrue(System.currentTimeMillis() < limite, "los reintentos tardan más de 150 s");
                        Hilos.dormir(500);
                    }
                }
                long espera = System.currentTimeMillis() - inicio;
                compose.arrancar("servicio-b");
                esperarInstancia(PB);
                assertEquals(n, exitos);
                long pedidos = mongo.getDatabase(BD).getCollection("peticiones").countDocuments(Filters.regex("_id", "^s3-clave-"));
                assertEquals(n, pedidos);
                verificar("docker kill de una instancia a mitad de 60 'crear pedido'", "%d respondieron antes de morir, %d cortados; reintentos con la misma clave: %d pedidos en %d s (%d respuestas 409/503/tiempo agotado)"
                        .formatted(respondidos.get(), cortados.get(), pedidos, espera / 1000, intermedias));
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    /** Carga continua durante {@code ms}: lecturas de productos y creación de pedidos. Devuelve {peticiones, latencia media ms, respuestas 5xx}. */
    static long[] cargaLigera(long ms) {
        long fin = System.currentTimeMillis() + ms;
        AtomicLong n = new AtomicLong(), total = new AtomicLong(), e5xx = new AtomicLong();
        Hilos.aLaVez(4, h -> () -> {
            int puerto = h % 2 == 0 ? PA : PB;
            while (System.currentTimeMillis() < fin) {
                long a = System.nanoTime();
                HttpResponse<String> r = (n.get() % 3 == 0)
                        ? pedir(puerto, "carga-" + seq.incrementAndGet(), "carga-clave-" + seq.get() + "-" + System.nanoTime(), "P" + (10 + h) + ",P" + (20 + h))
                        : get(puerto, "/productos/P" + (1 + (int) (n.get() % 50)));
                total.addAndGet((System.nanoTime() - a) / 1_000_000);
                n.incrementAndGet();
                if (r.statusCode() >= 500) e5xx.incrementAndGet();
                Hilos.dormir(100);
            }
            return null;
        });
        return new long[]{n.get(), total.get() / Math.max(1, n.get()), e5xx.get()};
    }

    /** Hasta que la instancia vuelve a dar aciertos de caché y deja de acumular errores de Redis. Devuelve los milisegundos. */
    static long esperarRecuperacion(int puerto) throws Exception {
        long t0 = System.currentTimeMillis();
        long aciertos = metrica(puerto, "cacheAciertos"), erroresPrevios = -1;
        int estables = 0;
        while (System.currentTimeMillis() - t0 < 90_000) {
            for (int k = 1; k <= 10; k++) get(puerto, "/productos/P" + k);
            long errores = metrica(puerto, "erroresRedis");
            if (metrica(puerto, "cacheAciertos") > aciertos + 5 && errores == erroresPrevios) { if (++estables >= 2) return System.currentTimeMillis() - t0; } else estables = 0;
            erroresPrevios = errores;
            Hilos.dormir(500);
        }
        fail("la instancia " + puerto + " no recuperó la caché en 90 s");
        return -1;
    }

    @Test @Order(4)
    void s4_redisDetenidoDosMinutos() throws Exception {
        carga.pausar();                                             // en esta prueba la carga la pone la propia prueba, para medir
        volcarMetricasSiFalla(() -> {
            try {
                long[] antes = cargaLigera(5000);
                long aciertos = suma("cacheAciertos");
                compose.parar("redis");
                long[] durante;
                try { durante = cargaLigera(120_000); }
                finally { compose.arrancar("redis"); }
                for (int i = 0; i < 60; i++) { try { redis.ping(); break; } catch (RuntimeException e) { Hilos.dormir(500); } }
                long recA = esperarRecuperacion(PA), recB = esperarRecuperacion(PB);
                assertEquals(0, durante[2], "ninguna respuesta 5xx con Redis detenido");
                assertTrue(suma("erroresRedis") > 0);
                assertTrue(suma("cacheAciertos") > aciertos);
                verificar("Redis detenido 120 s con carga continua",
                        "%d peticiones, 0 errores 5xx; latencia media %d ms con Redis, %d ms sin él; caché recuperada en %d ms (a) y %d ms (b) tras volver".formatted(durante[0], antes[1], durante[1], recA, recB));
                Salida.linea("recuperacion.txt", "Tras volver Redis, cada instancia tarda %d ms (a) y %d ms (b) en volver a usar la caché; mientras tanto no hay caché, límite de tasa ni idempotencia del borde en esa instancia (abierto)".formatted(recA, recB));
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    @Test @Order(5)
    void s5_vaciarRedisEnPlenaCarga() throws Exception {
        volcarMetricasSiFalla(() -> {
            try {
                int productos = 10;
                for (int i = 1; i <= productos; i++) { get(PA, "/productos/P" + i); get(PB, "/productos/P" + i); }
                long basesAntes = suma("lecturasBase"), erroresAntes = suma("erroresRedis");
                AtomicLong n = new AtomicLong();
                Thread flush = new Thread(() -> { Hilos.dormir(500); redis.flushall(); });
                flush.start();
                long fin = System.currentTimeMillis() + 2000;
                Hilos.aLaVez(50, h -> () -> {
                    Random r = new Random(h);
                    while (System.currentTimeMillis() < fin) { get(h % 2 == 0 ? PA : PB, "/productos/P" + (1 + r.nextInt(productos))); n.incrementAndGet(); }
                    return null;
                });
                flush.join();
                long lecturas = suma("lecturasBase") - basesAntes, errores = suma("erroresRedis") - erroresAntes;
                assertEquals(0, errores, "Redis respondía: ningún error de Redis durante la prueba");
                // la carga de fondo también consulta G1 a G20: son productos distintos que se recargan una vez por instancia
                long maximo = 2L * (productos + 20);
                assertTrue(lecturas <= maximo + 5, "lecturas a la base: " + lecturas + ", máximo teórico " + maximo);
                verificar("FLUSHALL en plena carga (50 hilos, 2 instancias)", "%d peticiones, %d lecturas a MongoDB (máximo teórico: (%d productos de la prueba + 20 de la carga de fondo) x 2 instancias = %d)".formatted(n.get(), lecturas, productos, maximo));
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    @Test @Order(6)
    void s6_cienPreciosDirectosEnMongo() throws Exception {
        volcarMetricasSiFalla(() -> {
            try {
                for (int i = 1; i <= 100; i++) { get(PA, "/productos/P" + i); get(PB, "/productos/P" + i); }
                var productos = mongo.getDatabase(BD).getCollection("productos");
                for (int i = 1; i <= 100; i++) productos.updateOne(Filters.eq("_id", "P" + i), new Document("$set", new Document("precio", 7000L + i)));
                long t0 = System.currentTimeMillis();
                long[] max = new long[2];
                for (int k = 0; k < 2; k++) {
                    int puerto = k == 0 ? PA : PB;
                    for (int i = 1; i <= 100; i++) {
                        while (Document.parse(get(puerto, "/productos/P" + i).body()).get("precioCentavos", Number.class).longValue() != 7000L + i) {
                            assertTrue(System.currentTimeMillis() - t0 < 15_000);
                            Hilos.dormir(50);
                        }
                    }
                    max[k] = System.currentTimeMillis() - t0;
                }
                long tope = (long) (4000 * 1.2);
                assertTrue(Math.max(max[0], max[1]) <= tope + 800, "se prometió ver el cambio en como máximo TTL + azar = " + tope + " ms");
                verificar("100 precios cambiados directamente en MongoDB",
                        "todos visibles en %d ms (instancia a) y %d ms (b); promesa: TTL 4000 ms + 20 %% de azar = %d ms".formatted(max[0], max[1], tope));
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    @Test @Order(7)
    void s7_limiteDeTasaPorCliente() throws Exception {
        volcarMetricasSiFalla(() -> {
            try {
                long degradadoAntes = suma("limiteRedisCaido");
                AtomicInteger ok = new AtomicInteger(), limitados = new AtomicInteger(), otros = new AtomicInteger(), otrosOk = new AtomicInteger();
                Thread otro = new Thread(() -> {
                    try { for (int i = 0; i < 5; i++) { var r = pedir(PB, "tranquilo-" + i, "s7-t-" + i, "P30"); if (r.statusCode() == 201) otrosOk.incrementAndGet(); otros.incrementAndGet(); Hilos.dormir(1500); } }
                    catch (Exception e) { throw new RuntimeException(e); }
                });
                otro.start();
                long inicio = System.currentTimeMillis();
                for (int i = 0; i < 100; i++) {
                    var r = pedir(i % 2 == 0 ? PA : PB, "ruidoso", "s7-r-" + i, "P31");
                    if (r.statusCode() == 201) ok.incrementAndGet(); else if (r.statusCode() == 429) { limitados.incrementAndGet(); assertTrue(Integer.parseInt(r.headers().firstValue("Retry-After").orElseThrow()) >= 1); }
                    Hilos.dormir(100);
                }
                otro.join();
                long segundos = (System.currentTimeMillis() - inicio) / 1000;
                long maximo = 20 * (segundos / 10 + 1) + 2;            // 20 por ventana de 10 s: si la máquina va lenta, el envío dura más y entran más ventanas
                assertEquals(0, suma("limiteRedisCaido") - degradadoAntes, "el limitador no estuvo degradado durante la prueba");
                assertTrue(ok.get() >= 20 && ok.get() <= maximo && ok.get() + limitados.get() == 100 && limitados.get() >= 50, "ok=" + ok + " limitados=" + limitados + " en " + segundos + " s");
                assertEquals(5, otrosOk.get());
                verificar("un cliente envía 100 pedidos en 10 s (límite 20 por 10 s)", "%d aceptados, %d con 429 y Retry-After en %d s; otros clientes: %d de %d con 201".formatted(ok.get(), limitados.get(), segundos, otrosOk.get(), otros.get()));
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    @Test @Order(8)
    void s8_detenerElPublicadorDeLaBandeja() throws Exception {
        volcarMetricasSiFalla(() -> {
            try {
                var control = mongo.getDatabase(BD).getCollection("control");
                control.replaceOne(Filters.eq("_id", "publicador"), new Document("_id", "publicador").append("pausado", true), new com.mongodb.client.model.ReplaceOptions().upsert(true));
                try {
                    Hilos.dormir(1000);
                    long publicadosAntes = suma("eventosPublicados");
                    for (int i = 0; i < 10; i++) assertEquals(201, pedir(i % 2 == 0 ? PA : PB, "s8-" + i, "s8-clave-" + i, "P40").statusCode());
                    long pendientes = metrica(PA, "eventosPendientes");
                    assertTrue(pendientes >= 10, "pendientes " + pendientes);
                    assertEquals(publicadosAntes, suma("eventosPublicados"));
                    control.replaceOne(Filters.eq("_id", "publicador"), new Document("_id", "publicador").append("pausado", false));
                    carga.pausar();
                    long t0 = System.currentTimeMillis();
                    while (metrica(PA, "eventosPendientes") > 0) { assertTrue(System.currentTimeMillis() - t0 < 15_000); Hilos.dormir(200); }
                    verificar("publicador de la bandeja detenido, 10 pedidos, publicador reiniciado", "pedidos aceptados con 201; %d eventos pendientes; todos publicados %d ms después de reiniciar".formatted(pendientes, System.currentTimeMillis() - t0));
                } finally { control.replaceOne(Filters.eq("_id", "publicador"), new Document("_id", "publicador").append("pausado", false), new com.mongodb.client.model.ReplaceOptions().upsert(true)); }
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    @Test @Order(9)
    void s9_pausarAlDuenoDelCerrojo() throws Exception {
        carga.pausar();
        volcarMetricasSiFalla(() -> {
            try {
                var db = mongo.getDatabase(BD);
                // 400 abandonados nuevos, creados por la API con el umbral normal (30 min): nadie los cancela todavía
                Hilos.aLaVez(20, h -> () -> { for (int i = 0; i < 20; i++) assertEquals(201, pedir(h % 2 == 0 ? PA : PB, "s9-" + h, "s9-clave-" + h + "-" + i, "P50").statusCode()); return null; });
                long pendientes = db.getCollection("pedidos").countDocuments(Filters.eq("estado", "PENDIENTE"));
                long eventosAntes = db.getCollection("bandeja").countDocuments(Filters.eq("tipo", "PedidoCancelado")), canceladosAntes = db.getCollection("pedidos").countDocuments(Filters.eq("estado", "CANCELADO"));
                // las dos instancias se recrean con umbral de 1 s y vida del cerrojo de 2 s: de golpe hay `pendientes` abandonados (los nuevos y los que dejaron las pruebas anteriores)
                levantar(Map.of("UMBRAL_CANCELACION_MS", "1000", "VIDA_CERROJO_MS", "2000"));
                int dueno = -1;
                long t0 = System.currentTimeMillis();
                String contenedor = null;
                while (contenedor == null) {
                    assertTrue(System.currentTimeMillis() - t0 < 15_000, "nadie tomó el cerrojo");
                    if (metricas(PA).getBoolean("tieneCerrojo")) { dueno = 0; contenedor = "servicio-a"; } else if (metricas(PB).getBoolean("tieneCerrojo")) { dueno = 1; contenedor = "servicio-b"; }
                }
                compose.pausar(contenedor);                                       // docker pause
                long pendientesAlPausar = db.getCollection("pedidos").countDocuments(Filters.eq("estado", "PENDIENTE"));
                int otra = dueno == 0 ? PB : PA;
                t0 = System.currentTimeMillis();
                while (db.getCollection("pedidos").countDocuments(Filters.eq("estado", "PENDIENTE")) > 0) {
                    assertTrue(System.currentTimeMillis() - t0 < 150_000, "la otra instancia debe tomar el relevo");
                    Hilos.dormir(300);
                }
                long relevo = System.currentTimeMillis() - t0;
                compose.reanudar(contenedor);                                      // docker unpause: el dueño caducado despierta y sigue con su lista
                Hilos.dormir(4000);
                long canceladosOtra = metrica(otra, "canceladosPorTarea"), canceladosDueno = metrica(dueno == 0 ? PA : PB, "canceladosPorTarea");
                long rechazados = suma("turnosRechazados");
                long eventos = db.getCollection("bandeja").countDocuments(Filters.eq("tipo", "PedidoCancelado"));
                assertEquals(0, db.getCollection("pedidos").countDocuments(Filters.eq("estado", "PENDIENTE")));
                // el contador de cada instancia no cuenta lo que canceló un lote que se abortó a medias por turno caducado; la base sí lo sabe todo
                long cancelados = db.getCollection("pedidos").countDocuments(Filters.eq("estado", "CANCELADO"));
                assertEquals(pendientes, cancelados - canceladosAntes, "cada pendiente se canceló exactamente una vez");
                assertEquals(pendientes, eventos - eventosAntes, "un evento PedidoCancelado por cada cancelación, ni uno más");
                assertTrue(canceladosOtra + canceladosDueno <= pendientes && canceladosOtra + canceladosDueno >= pendientes - 200, "los contadores de las instancias cuadran salvo el lote abortado del dueño caducado (máximo 200)");
                verificar("docker pause del dueño del cerrojo (vida 2 s, umbral 1 s)",
                        "pendientes de partida %d, todos cancelados una vez y con un evento cada uno; contadores: dueño %d, la otra %d tras el relevo (%d s); turnos rechazados %d; pendientes al pausar %d"
                                .formatted(pendientes, canceladosDueno, canceladosOtra, relevo / 1000, rechazados, pendientesAlPausar));
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    /** Con el umbral de 1 s la tarea cancela también lo que dejaron las pruebas anteriores: se espera a que termine antes de medir. */
    static long esperarQueNoHayaPendientes(com.mongodb.client.MongoDatabase db) {
        long t0 = System.currentTimeMillis();
        long pendientes;
        while ((pendientes = db.getCollection("pedidos").countDocuments(Filters.eq("estado", "PENDIENTE"))) > 0) {
            assertTrue(System.currentTimeMillis() - t0 < 120_000, "quedan pendientes: " + pendientes);
            Hilos.dormir(500);
        }
        return pendientes;
    }

    @Test @Order(10)
    void s10_laTecnologiaSoloEstaEnAdaptadoresYComposicion() {
        List<Path> fuentes = new ArrayList<>();
        for (String m : List.of("04-mongodb-en-el-banco-hexagonal", "05-redis-como-cache", "06-redis-mas-alla-de-la-cache", "07-taller-integrador"))
            fuentes.addAll(comun.Arquitectura.fuentes(Path.of("../../..", m, "taller/solucion-de-referencia/src/main/java")));
        fuentes.addAll(comun.Arquitectura.fuentes(Path.of("../../../../hexagonal/08-pruebas-y-limites/taller/solucion-de-referencia/src")));
        var violaciones = comun.Arquitectura.violaciones(fuentes);
        Salida.linea(R, "| prueba de arquitectura: %d archivos (núcleo y módulos 04 a 07), %d violaciones | %s | - |".formatted(fuentes.size(), violaciones.size(), violaciones.isEmpty() ? "ok" : violaciones.toString()));
        assertEquals(List.of(), violaciones);
    }
}
