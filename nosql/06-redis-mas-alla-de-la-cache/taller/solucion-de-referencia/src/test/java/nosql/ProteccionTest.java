package nosql;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import comun.Arquitectura;
import comun.Docker;
import comun.Entorno;
import comun.Hilos;
import comun.RedisUtil;
import comun.Salida;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.api.sync.RedisCommands;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import nosql.adaptadores.redis.ClienteRedis;
import nosql.adaptadores.redis.IdempotenciaRedis;
import nosql.adaptadores.redis.LimiteDeTasaRedis;
import nosql.aplicacion.PoliticaDeReintento;
import nosql.aplicacion.TurnoCaducado;
import nosql.proteccion.Composicion.Config;
import nosql.proteccion.Composicion.Grafo;
import org.bson.Document;
import org.junit.jupiter.api.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProteccionTest {
    static final String R = "resultados.txt";
    static final int PUERTO = 18061;
    static final String CUERPO = "{\"cliente\":\"c1\",\"lineas\":[{\"producto\":\"A\",\"cantidad\":1}]}";
    static RedisClient cliente;
    static RedisCommands<String, String> redis;
    static HttpClient http = HttpClient.newHttpClient();

    @BeforeAll static void preparar() {
        Salida.limpiar(R);
        cliente = ClienteRedis.conTiemposCortos(Entorno.redisUrl(), Duration.ofMillis(500));
        redis = cliente.connect().sync();
    }
    @BeforeEach void vaciar() { RedisUtil.vaciar(redis); }
    @AfterAll static void cerrar() { RedisUtil.vaciar(redis); cliente.shutdown(); }

    static HttpResponse<String> post(String ruta, String clave, String cuerpo) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + PUERTO + ruta)).POST(HttpRequest.BodyPublishers.ofString(cuerpo));
        if (clave != null) b.header("Idempotency-Key", clave);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    static Composicion.Sistema sistema(String bd, PoliticaDeReintento politica) {
        var s = Composicion.elegir("mongo", Entorno.mongoUrl(), bd, politica, Clock.systemUTC());
        s.mongo().cliente().getDatabase(bd + "_pagos").drop();
        return s;
    }

    static void cerrarTodo(Composicion.Sistema s, Grafo g) {
        if (g != null) g.http().close();
        s.mongo().cliente().getDatabase(s.mongo().db().getName() + "_pagos").drop();
        s.borrarYCerrar().run();
    }

    static long pedidos(Composicion.Sistema s) { return s.mongo().db().getCollection("pedidos").countDocuments(); }

    // ---------------------------------------------------------------- 1
    @Test @Order(1)
    void paso1_idempotenciaDelBorde() throws Exception {
        var s = sistema("t06_p1", PoliticaDeReintento.esperaCreciente(50, 10, 1000));
        Grafo g = null;
        try {
            s.siembra().producto("A", "A", 1000, 100_000);
            g = Composicion_montar(s, Config.por(10_000, 60_000));
            for (int ronda = 1; ronda <= 10; ronda++) {
                String clave = "clave-" + ronda;
                long antes = pedidos(s);
                List<HttpResponse<String>> r = Hilos.aLaVez(10, i -> () -> post("/pedidos", clave, CUERPO));
                Map<Integer, Long> porStatus = new TreeMap<>();
                r.forEach(x -> porStatus.merge(x.statusCode(), 1L, Long::sum));
                Set<String> ids = new HashSet<>();
                for (var x : r) if (x.statusCode() == 201) ids.add(Document.parse(x.body()).getString("id"));
                long cobros = s.mongo().cliente().getDatabase("t06_p1_pagos").getCollection("cobros").countDocuments(Filters.eq("_id", clave));
                Salida.linea(R, "PASO 1 ronda %2d: 10 peticiones idénticas -> %s; pedidos creados %d, cobros de esta clave %d, ids distintos en las 201: %d"
                        .formatted(ronda, porStatus, pedidos(s) - antes, cobros, ids.size()));
                assertEquals(1, pedidos(s) - antes);
                assertEquals(1, cobros);
                assertEquals(1, ids.size());
                assertTrue(porStatus.keySet().stream().allMatch(c -> c == 201 || c == 409));
                assertEquals(1, porStatus.get(201));
            }
            var otroCuerpo = post("/pedidos", "clave-1", "{\"cliente\":\"c1\",\"lineas\":[{\"producto\":\"A\",\"cantidad\":2}]}");
            Salida.linea(R, "  la misma clave con otro cuerpo -> " + otroCuerpo.statusCode());
            assertEquals(422, otroCuerpo.statusCode());
            var repetida = post("/pedidos", "clave-1", CUERPO);
            assertEquals(201, repetida.statusCode());
            assertEquals(10, pedidos(s));
        } finally { cerrarTodo(s, g); }
    }

    static Grafo Composicion_montar(Composicion.Sistema s, Config config) {
        return nosql.proteccion.Composicion.montar(s, s.productos(), redis, PUERTO, config, null);
    }

    // ---------------------------------------------------------------- 2
    static long correr(String modo, String clave, String bd, long pausaMs, boolean matar) throws Exception {
        List<String> cmd = List.of(System.getProperty("java.home") + "/bin/java", "-Dorg.slf4j.simpleLogger.defaultLogLevel=error", "-cp", System.getProperty("java.class.path"),
                "nosql.ProcesoDeCobro", Entorno.mongoUrl(), bd, Entorno.redisUrl(), modo, clave, "" + pausaMs, "2000");
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String linea;
        long status = -1;
        while ((linea = in.readLine()) != null) {
            if (linea.equals("COBRADO") && matar) { p.destroyForcibly(); p.waitFor(); return -9; }   // kill -9 entre el cobro y el guardado
            if (linea.startsWith("RESPUESTA")) status = Long.parseLong(linea.split(" ")[1]);
        }
        p.waitFor();
        return status;
    }

    @Test @Order(2)
    void paso2_elHuecoEntreCobrarYApuntar() throws Exception {
        for (String modo : List.of("ingenuo", "idempotente")) {
            String bd = "t06_p2_" + modo;
            var s = sistema(bd, PoliticaDeReintento.inmediata(3));
            try {
                s.siembra().producto("A", "A", 1000, 100);
                long primero = correr(modo, "k-" + modo, bd, 60_000, true);
                Hilos.dormir(2500);                                  // vence el EN_CURSO de la clave (2 s) que dejó el proceso muerto
                long reintento = correr(modo, "k-" + modo, bd, 0, false);
                long cobros = s.mongo().cliente().getDatabase(bd + "_pagos").getCollection("cobros").countDocuments();
                Salida.linea(R, "PASO 2 pasarela %-11s: proceso 1 muerto con kill -9 (%d), reintento -> %d; pedidos %d, cobros %d".formatted(modo, primero, reintento, pedidos(s), cobros));
                assertEquals(-9, primero);
                assertEquals(1, pedidos(s));
                assertEquals(modo.equals("ingenuo") ? 2 : 1, cobros);
                assertEquals(200, reintento, "el pedido ya existía: la transacción lo reconoce por la clave");
                assertEquals(99, s.productos().porId("A").orElseThrow().existencias(), "las existencias se descontaron una sola vez");
            } finally { cerrarTodo(s, null); }
        }
    }

    // ---------------------------------------------------------------- 3
    @Test @Order(3)
    void paso3_limiteDeTasaYBordeDeLaVentana() throws Exception {
        long W = 3000;
        var l = new LimiteDeTasaRedis(redis, 10, W, LimiteDeTasaRedis.SiRedisFalla.ABIERTO);
        List<Long> aceptadas = new ArrayList<>();
        java.util.function.Supplier<Boolean> uno = () -> { var d = l.intentar("u"); if (d.permitido()) aceptadas.add(System.currentTimeMillis()); return d.permitido(); };
        int ok = 0;
        for (int i = 0; i < 5; i++) if (uno.get()) ok++;
        Hilos.dormir((long) (0.6 * W));
        for (int i = 0; i < 5; i++) if (uno.get()) ok++;
        assertEquals(10, ok);
        Hilos.dormir((long) (0.3 * W));                              // a 0,9 W: hay 10 dentro de la ventana
        var rechazo = l.intentar("u");
        assertFalse(rechazo.permitido());
        Salida.linea(R, "PASO 3. 5 en t=0, 5 en 0,6 W (límite 10 por W=3 s); a 0,9 W: rechazada, reintentar en %d ms".formatted(rechazo.reintentarEnMs()));
        assertTrue(rechazo.reintentarEnMs() > 0 && rechazo.reintentarEnMs() <= (long) (0.15 * W) + 100);
        Hilos.dormir((long) (0.15 * W));                             // a 1,05 W: caducaron los 5 primeros, pero no los otros 5
        int despues = 0;
        for (int i = 0; i < 8; i++) if (uno.get()) despues++;
        Salida.linea(R, "  a 1,05 W (cruzando el borde de una ventana fija): solo pasan %d de 8, no 10 ni 8".formatted(despues));
        assertEquals(5, despues);
        Collections.sort(aceptadas);
        for (int i = 0; i + 10 < aceptadas.size(); i++) assertTrue(aceptadas.get(i + 10) - aceptadas.get(i) >= W - 100, "más de 10 en una ventana");
        Salida.linea(R, "  %d peticiones aceptadas en total; ninguna ventana de %d ms contiene más de 10".formatted(aceptadas.size(), W - 100));

        // por HTTP: 429 con Retry-After, y otro cliente no lo nota
        var s = sistema("t06_p3", PoliticaDeReintento.esperaCreciente(50, 10, 1000));
        Grafo g = null;
        try {
            s.siembra().producto("A", "A", 1000, 1000);
            g = Composicion_montar(s, Config.por(10, 60_000));
            int creados = 0, limitados = 0;
            String retry = null;
            for (int i = 0; i < 100; i++) {
                var r = post("/pedidos", null, CUERPO.replace("c1", "ruidoso"));
                if (r.statusCode() == 201) creados++;
                if (r.statusCode() == 429) { limitados++; retry = r.headers().firstValue("Retry-After").orElse(null); }
            }
            var otro = post("/pedidos", null, CUERPO.replace("c1", "tranquilo"));
            Salida.linea(R, "  HTTP: un cliente envía 100 pedidos seguidos -> %d creados, %d con 429 (Retry-After: %s s); otro cliente -> %d".formatted(creados, limitados, retry, otro.statusCode()));
            assertEquals(10, creados);
            assertEquals(90, limitados);
            assertNotNull(retry);
            assertEquals(201, otro.statusCode());
        } finally { cerrarTodo(s, g); }
    }

    // ---------------------------------------------------------------- 4
    @Test @Order(4)
    void paso4_sinRedis() throws Exception {
        var s = sistema("t06_p4", PoliticaDeReintento.esperaCreciente(50, 10, 1000));
        Grafo g = null;
        Docker.parar("redis");
        try {
            s.siembra().producto("A", "A", 1000, 1000);
            g = Composicion_montar(s, Config.por(10, 60_000));
            List<HttpResponse<String>> r = Hilos.aLaVez(10, i -> () -> post("/pedidos", "sin-redis", CUERPO));
            long ok = r.stream().filter(x -> x.statusCode() == 201 || x.statusCode() == 200).count();
            Set<String> ids = new HashSet<>();
            r.forEach(x -> { if (x.statusCode() == 201 || x.statusCode() == 200) ids.add(Document.parse(x.body()).getString("id")); });
            long cobros = g.pasarela().cobros();
            Salida.linea(R, "PASO 4. Redis detenido, límite ABIERTO: 10 peticiones con la misma clave -> %d respuestas 2xx, %d pedidos distintos, %d en la base, %d cobros; operaciones degradadas %d"
                    .formatted(ok, ids.size(), pedidos(s), cobros, g.idempotencia().degradadas.get()));
            assertEquals(10, ok);
            assertEquals(1, ids.size());
            assertEquals(1, pedidos(s));
            assertEquals(1, cobros);
            g.http().close();
            var cerrado = nosql.proteccion.Composicion.montar(s, s.productos(), redis, PUERTO, Config.por(10, 60_000).conPolitica(LimiteDeTasaRedis.SiRedisFalla.CERRADO), null);
            try {
                var rc = post("/pedidos", null, CUERPO.replace("c1", "c2"));
                Salida.linea(R, "  la alternativa CERRADO rechazaría con " + rc.statusCode() + " y el servicio dejaría de vender mientras Redis esté caído");
                assertEquals(503, rc.statusCode());
            } finally { cerrado.http().close(); }
        } finally {
            Docker.arrancar("redis");
            cerrarTodo(s, null);
            esperarRedis();
        }
    }

    static void esperarRedis() {
        for (int i = 0; i < 60; i++) { try { redis.ping(); return; } catch (RuntimeException e) { Hilos.dormir(500); } }
        fail("Redis no volvió");
    }

    // ---------------------------------------------------------------- 5 y 6
    /** Un pedido PENDIENTE de hace dos horas: se crea con el servicio y se envejece directamente en la base. */
    static String pedidoViejo(Composicion.Sistema s, String clave, int unidades) {
        String id = s.crear().crear(Pruebas.solicitud(clave, "c1", "A:" + unidades)).pedido().id();
        Date hace2h = Date.from(Instant.now().minusSeconds(7200));
        s.mongo().db().getCollection("pedidos").updateOne(Filters.eq("_id", id), Updates.combine(Updates.set("fecha", hace2h)));
        return id;
    }

    static String estado(Composicion.Sistema s, String id) { return s.consultar().porId(id).orElseThrow().estado(); }

    @Test @Order(5)
    void paso5_cerrojoConTurno() {
        var s = sistema("t06_p5", PoliticaDeReintento.inmediata(3));
        Grafo g = null;
        try {
            s.siembra().producto("A", "A", 1000, 100);
            g = Composicion_montar(s, Config.por(1000, 60_000));
            var cerrojo = g.cerrojo();
            var limite = Instant.now().minusSeconds(1800);
            var v0 = pedidoViejo(s, "v0", 2); var v1 = pedidoViejo(s, "v1", 2);
            // tres instancias compiten: solo una obtiene el cerrojo
            var r = Hilos.aLaVez(3, i -> () -> cerrojo.adquirir("cancelar-abandonados", 800));
            assertEquals(1, r.stream().filter(Optional::isPresent).count());
            var A = r.stream().filter(Optional::isPresent).findFirst().get().get();
            Salida.linea(R, "PASO 5. tres instancias piden el cerrojo: una lo obtiene (turno %d), las otras dos no".formatted(A.turno()));
            Hilos.dormir(1200);                                       // A se pausa más que la vida del cerrojo (800 ms)
            var B = cerrojo.adquirir("cancelar-abandonados", 800).orElseThrow();
            assertTrue(B.turno() > A.turno());
            var resB = g.cancelarAbandonados().cancelar("cancelar-abandonados", B.turno(), limite, 100);
            Salida.linea(R, "  A estuvo parada 1200 ms (vida del cerrojo 800 ms); B obtuvo el cerrojo con el turno %d y canceló %d pedidos".formatted(B.turno(), resB.canceladas()));
            assertEquals(2, resB.canceladas());
            var v2 = pedidoViejo(s, "v2", 2);                         // aparece otro abandonado cuando A despierta
            final Grafo gf = g;
            var e = assertThrows(TurnoCaducado.class, () -> gf.cancelarAbandonados().cancelar("cancelar-abandonados", A.turno(), limite, 100));
            Salida.linea(R, "  A despierta e intenta cancelar %s con el turno %d: rechazada (%s); el pedido sigue %s".formatted(v2, A.turno(), e.getMessage(), estado(s, v2)));
            assertEquals("PENDIENTE", estado(s, v2));
            assertEquals(98, s.productos().porId("A").orElseThrow().existencias(), "las existencias de v0 y v1 volvieron; v2 sigue reservado");
            assertFalse(cerrojo.liberar(A), "A ya no es la dueña: su ficha no libera el cerrojo de B");
            assertTrue(cerrojo.liberar(B));
            // control: sin comprobar el turno, la misma escritura de A se aplicaría
            s.mongo().db().getCollection("pedidos").updateOne(Filters.and(Filters.eq("_id", v2), Filters.eq("estado", "PENDIENTE")), Updates.set("estado", "CANCELADO"));
            Salida.linea(R, "  control sin turno: la misma escritura de A, sin comprobarlo, se aplica");
            // los pedidos que cancela la tarea emiten su evento, como los que cancela el servicio
            assertEquals(2, s.mongo().db().getCollection("bandeja").countDocuments(Filters.eq("tipo", "PedidoCancelado")));
        } finally { cerrarTodo(s, g); }
    }
    /** Qué pasa si Redis olvida: el contador de turnos no vive en Redis, así que un FLUSHALL no deja parado al dueño legítimo. */
    @Test @Order(5)
    void paso5b_siRedisOlvidaElTurnoElSiguienteDuenoSigueCancelando() {
        var s = sistema("t06_p5b", PoliticaDeReintento.inmediata(3));
        Grafo g = null;
        try {
            s.siembra().producto("A", "A", 1000, 100);
            g = Composicion_montar(s, Config.por(1000, 60_000));
            var limite = Instant.now().minusSeconds(1800);
            for (int i = 0; i < 4; i++) { // turnos altos
                var c = g.cerrojo().adquirir("cancelar-abandonados", 5000).orElseThrow();
                pedidoViejo(s, "w" + i, 1);
                assertEquals(1, g.cancelarAbandonados().cancelar("cancelar-abandonados", c.turno(), limite, 100).canceladas());
                g.cerrojo().liberar(c);
            }
            long ultimo = g.turnos().siguiente("cancelar-abandonados");
            redis.flushall();                                         // Redis olvida: cerrojo y todo lo suyo
            assertNull(redis.get("pedidos:cerrojo:v1:cancelar-abandonados:turno"), "no hay contador en Redis");
            var c = g.cerrojo().adquirir("cancelar-abandonados", 5000).orElseThrow();
            assertTrue(c.turno() > ultimo, "el turno sigue creciendo desde MongoDB");
            var vNuevo = pedidoViejo(s, "w-despues", 1);
            var res = g.cancelarAbandonados().cancelar("cancelar-abandonados", c.turno(), limite, 100);
            Salida.linea(R, "PASO 5b. tras 4 turnos y un FLUSHALL de Redis, el siguiente dueño recibe el turno %d (> %d) y cancela %d pedido(s) (%s -> %s)"
                    .formatted(c.turno(), ultimo, res.canceladas(), vNuevo, estado(s, vNuevo)));
            assertEquals(1, res.canceladas());
            assertEquals("CANCELADO", estado(s, vNuevo));
            // y un dueño anterior que sigue vivo (turno viejo) es rechazado aunque el cerrojo ya no exista
            pedidoViejo(s, "w-final", 1);
            assertThrows(TurnoCaducado.class, () -> g_cancel(s, c.turno() - 1, limite));
        } finally { cerrarTodo(s, g); }
    }

    static void g_cancel(Composicion.Sistema s, long turno, Instant limite) {
        var m = s.mongo();
        var turnos = new nosql.adaptadores.mongo.TurnosMongo(m.db(), m.unidad());
        new nosql.aplicacion.CancelarAbandonados(new nosql.adaptadores.mongo.AbandonadosMongo(m.db()), m.pedidos(), m.inventario(), turnos, m.unidad(), m.bandeja(), m.reloj())
                .cancelar("cancelar-abandonados", turno, limite, 100);
    }

    @Test @Order(6)
    void paso6_hacíaFaltaElCerrojo() {
        var s = sistema("t06_p6", PoliticaDeReintento.esperaCreciente(50, 10, 1000));
        Grafo g = null;
        try {
            int n = 60;
            s.siembra().producto("A", "A", 1000, 1000);
            g = Composicion_montar(s, Config.por(1000, 60_000));
            var pedidos = s.mongo().db().getCollection("pedidos");
            var prods = s.mongo().db().getCollection("productos");
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < n; i++) ids.add(pedidoViejo(s, "v" + i, 2));        // existencias 1000 - 120 = 880
            // ingenuo: cancela con filtro PENDIENTE, pero devuelve las existencias siempre
            AtomicInteger cancelaciones = new AtomicInteger();
            Hilos.aLaVez(2, k -> () -> {
                for (String id : ids) {
                    long mod = pedidos.updateOne(Filters.and(Filters.eq("_id", id), Filters.eq("estado", "PENDIENTE")), Updates.set("estado", "CANCELADO")).getModifiedCount();
                    cancelaciones.addAndGet((int) mod);
                    prods.updateOne(Filters.eq("_id", "A"), Updates.inc("existencias", 2));       // devolución sin comprobar quién canceló
                }
                return null;
            });
            int stock = prods.find().first().getInteger("existencias");
            Salida.linea(R, "PASO 6. dos instancias a la vez, sin cerrojo, filtro estado=PENDIENTE: pedidos cancelados %d de %d (ninguno dos veces), pero existencias %d en vez de 1000 (devolución duplicada: %d unidades de más)"
                    .formatted(cancelaciones.get(), n, stock, stock - 1000));
            assertEquals(n, cancelaciones.get());
            assertTrue(stock > 1000);
            // correcto: la devolución va en la misma transacción que la cancelación y solo si esta instancia fue la que canceló
            prods.updateOne(Filters.eq("_id", "A"), Updates.combine(Updates.set("existencias", 1000 - 2 * n), Updates.inc("version", 1L)));
            pedidos.updateMany(new Document(), Updates.set("estado", "PENDIENTE"));
            var limite = Instant.now().minusSeconds(1800);
            var tarea = g.cancelarAbandonados();
            AtomicInteger canceladas = new AtomicInteger(), conflictos = new AtomicInteger();
            Hilos.aLaVez(2, k -> () -> {
                for (int pasada = 0; pasada < 20; pasada++) {      // un conflicto deja el pedido para la pasada siguiente
                    var r = tarea.cancelar("t6", 1, limite, 1000);
                    canceladas.addAndGet(r.canceladas()); conflictos.addAndGet(r.enConflicto());
                    if (r.canceladas() == 0 && r.enConflicto() == 0) break;
                }
                return null;
            });
            int stock2 = prods.find().first().getInteger("existencias");
            Salida.linea(R, "  con la devolución en la misma transacción que la cancelación: cancelados %d, existencias %d (exacto), %d choques entre las dos instancias reintentados en la pasada siguiente"
                    .formatted(canceladas.get(), stock2, conflictos.get()));
            assertEquals(n, canceladas.get());
            assertEquals(1000, stock2);
            assertEquals(n, s.mongo().db().getCollection("bandeja").countDocuments(Filters.eq("tipo", "PedidoCancelado")), "un evento por cada cancelación, ni uno más");
        } finally { cerrarTodo(s, g); }
    }

    // ---------------------------------------------------------------- 7
    @Test @Order(7)
    void paso7_desalojoDeClavesDeIdempotencia() throws Exception {
        redis.configSet("maxmemory", "5mb");
        redis.configSet("maxmemory-policy", "allkeys-lru");
        try {
            var idem = new IdempotenciaRedis(redis, 30_000);
            String cuerpo = "x".repeat(500);
            for (int i = 0; i < 3000; i++) idem.completar("k" + i, "h", 201, cuerpo);
            assertEquals(0, idem.degradadas.get(), "ninguna escritura de idempotencia falló al llenar");
            long antes = redis.keys(IdempotenciaRedis.PREFIJO + "*").size();
            // Con allkeys-lru Redis desaloja en lugar de rechazar, pero bajo presión (máquina cargada, desalojo en segundo plano) se vio una vez un
            // "OOM command not allowed" transitorio. No se reprodujo en el laboratorio; la prueba lo cuenta, guarda el diagnóstico y reintenta la escritura.
            int oomTransitorios = 0;
            for (int i = 0; i < 60_000; i++) {
                for (int intento = 0; ; intento++) {
                    try { redis.set("pedidos:catalogo:v1:producto:" + i, "x".repeat(150), io.lettuce.core.SetArgs.Builder.ex(600)); break; }
                    catch (RedisCommandExecutionException oom) {
                        if (!oom.getMessage().startsWith("OOM") || intento >= 20) throw oom;
                        if (oomTransitorios++ == 0) {
                            Files.createDirectories(Path.of("salida"));
                            Files.writeString(Path.of("salida", "redis-oom-paso7.txt"), oom.getMessage() + "\n" + RedisUtil.diagnostico(redis));
                        }
                        Hilos.dormir(50);
                    }
                }
            }
            Salida.linea(R, "  OOM transitorios con allkeys-lru durante el llenado (reintentados): " + oomTransitorios);
            long despues = redis.keys(IdempotenciaRedis.PREFIJO + "*").size();
            Salida.linea(R, "PASO 7. maxmemory 5mb + allkeys-lru: claves de idempotencia antes de llenar la caché %d, después %d (desalojadas %d: esas peticiones podrían ejecutarse dos veces)"
                    .formatted(antes, despues, antes - despues));
            assertEquals(3000, antes);
            assertTrue(despues < antes);
        } finally {
            redis.configSet("maxmemory", "0");
            redis.configSet("maxmemory-policy", "noeviction");
        }
        // la configuración correcta: una instancia para el estado (noeviction), otra para la caché (allkeys-lru)
        var estadoCompose = new comun.Compose(System.getProperty("infra.proyecto", "nosql") + "-estado", Path.of("estado.yaml").toAbsolutePath().toString(), java.util.Map.of());
        estadoCompose.arriba();
        RedisClient c2 = RedisClient.create("redis://localhost:6380");
        try {
            var estado = c2.connect().sync();
            estado.flushall(io.lettuce.core.FlushMode.SYNC);
            String error = null;
            int guardadas = 0;
            try { for (int i = 0; i < 100_000; i++) { estado.set("pedidos:idem:v1:k" + i, "x".repeat(500)); guardadas++; } }
            catch (RedisCommandExecutionException e) { error = e.getMessage(); }
            long enBase = estado.dbsize();
            Salida.linea(R, "  instancia de estado (5mb, noeviction): guardó %d claves y luego respondió '%s'; no se pierde ninguna (dbsize %d)".formatted(guardadas, error, enBase));
            assertNotNull(error);
            assertTrue(error.startsWith("OOM"));
            assertEquals(guardadas, enBase);
            estado.flushall(io.lettuce.core.FlushMode.SYNC);
        } finally { c2.shutdown(); estadoCompose.abajo(); }
    }

    // ---------------------------------------------------------------- arquitectura
    @Test @Order(8)
    void tecnologiaSoloEnAdaptadoresYComposicion() {
        List<Path> fuentes = new ArrayList<>();
        for (String m : List.of("04-mongodb-en-el-banco-hexagonal", "05-redis-como-cache", "06-redis-mas-alla-de-la-cache"))
            fuentes.addAll(Arquitectura.fuentes(Path.of("../../..", m, "taller/solucion-de-referencia/src/main/java")));
        fuentes.addAll(Arquitectura.fuentes(Path.of("../../../../hexagonal/08-pruebas-y-limites/taller/solucion-de-referencia/src")));
        var violaciones = Arquitectura.violaciones(fuentes);
        Salida.linea(R, "ARQUITECTURA. %d archivos de los módulos 04 a 06 y del núcleo; violaciones (io.lettuce, com.mongodb, org.bson o com.sun.net.httpserver fuera de adaptadores/ y de la composición): %d"
                .formatted(fuentes.size(), violaciones.size()));
        assertEquals(List.of(), violaciones);
        assertTrue(Arquitectura.conTecnologiaEnAdaptadores(fuentes) >= 12);
    }
}
