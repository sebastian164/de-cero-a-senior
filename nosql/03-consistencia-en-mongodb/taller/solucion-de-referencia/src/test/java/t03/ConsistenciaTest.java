package t03;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import comun.Entorno;
import comun.Hilos;
import comun.Salida;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.Document;
import org.junit.jupiter.api.*;

/** Taller 03: existencias, estado con versión, crear pedido en transacción, conflictos y coste. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConsistenciaTest {
    static final String R = "resultados.txt";
    static MongoClient mongo;
    static MongoDatabase db;
    static MongoCollection<Document> productos, pedidos;
    static final TransactionOptions TX = TransactionOptions.builder().readPreference(ReadPreference.primary())
            .readConcern(ReadConcern.SNAPSHOT).writeConcern(WriteConcern.MAJORITY).build();

    @BeforeAll
    static void preparar() {
        Salida.limpiar(R);
        mongo = Entorno.mongo();
        db = mongo.getDatabase("t03");
        db.drop();
        productos = db.getCollection("productos");
        pedidos = db.getCollection("pedidos");
    }

    @AfterAll
    static void cerrar() { db.drop(); mongo.close(); }

    // ---------------------------------------------------------------- 1. existencias

    @Test @Order(1)
    void paso1_existencias() {
        Salida.linea(R, "PASO 1. 5 unidades, 20 compradores a la vez, diez veces");
        int rondasConSobreventa = 0;
        for (int ronda = 1; ronda <= 10; ronda++) {
            productos.drop();
            productos.insertOne(new Document("_id", "P1").append("existencias", 5));
            AtomicInteger vendidos = new AtomicInteger();
            Hilos.aLaVez(20, i -> () -> {
                int e = productos.find(Filters.eq("_id", "P1")).first().getInteger("existencias");   // lee
                if (e >= 1) {                                                                          // decide en Java
                    Hilos.dormir(1);
                    productos.updateOne(Filters.eq("_id", "P1"), Updates.set("existencias", e - 1));   // escribe lo que calculó
                    vendidos.incrementAndGet();
                }
                return null;
            });
            int stock = productos.find().first().getInteger("existencias");
            if (vendidos.get() > 5) rondasConSobreventa++;
            Salida.linea(R, "  lectura y escritura separadas, ronda %2d: vendidas %2d de 5, existencias finales %d".formatted(ronda, vendidos.get(), stock));
        }
        assertTrue(rondasConSobreventa > 0, "tiene que haber sobreventa en alguna ronda");
        Salida.linea(R, "  -> sobreventa en %d de 10 rondas".formatted(rondasConSobreventa));
        for (int ronda = 1; ronda <= 10; ronda++) {
            productos.drop();
            productos.insertOne(new Document("_id", "P1").append("existencias", 5));
            AtomicInteger vendidos = new AtomicInteger();
            Hilos.aLaVez(20, i -> () -> {
                if (productos.updateOne(Filters.and(Filters.eq("_id", "P1"), Filters.gte("existencias", 1)), Updates.inc("existencias", -1)).getModifiedCount() == 1) vendidos.incrementAndGet();
                return null;
            });
            int stock = productos.find().first().getInteger("existencias");
            Salida.linea(R, "  actualización condicional, ronda %2d: vendidas %d de 5, existencias finales %d".formatted(ronda, vendidos.get(), stock));
            assertEquals(5, vendidos.get());
            assertEquals(0, stock);
        }
    }

    // ---------------------------------------------------------------- 2. estado con versión

    static boolean transicionValida(String desde, String hacia) {
        return switch (hacia) {
            case "PAGADO", "CANCELADO" -> desde.equals("PENDIENTE");
            case "ENVIADO" -> desde.equals("PAGADO");
            default -> false;
        };
    }

    /** Lee, decide en Java y escribe. Con versión, la escritura solo vale si nadie escribió entre la lectura y ella. */
    static boolean transicionar(String id, String hacia, boolean conVersion) {
        while (true) {
            Document p = pedidos.find(Filters.eq("_id", id)).first();
            if (!transicionValida(p.getString("estado"), hacia)) return false;
            Hilos.dormir(2);                                       // el tiempo que tarda la regla de negocio
            Document paso = new Document("estado", hacia);
            var set = Updates.combine(Updates.set("estado", hacia), Updates.push("historial", new Document("estado", hacia).append("fecha", new Date())));
            if (!conVersion) {
                pedidos.updateOne(Filters.eq("_id", id), set);
                return true;
            }
            long v = p.getLong("version");
            if (pedidos.updateOne(Filters.and(Filters.eq("_id", id), Filters.eq("version", v)), Updates.combine(set, Updates.inc("version", 1))).getModifiedCount() == 1) return true;
            // alguien escribió antes: se vuelve a leer y a decidir con el estado nuevo
        }
    }

    @Test @Order(2)
    void paso2_estadoDelPedido() {
        Salida.linea(R, "PASO 2. pagar y cancelar a la vez sobre el mismo pedido");
        pedidos.drop();
        int imposibles = 0;
        for (int t = 1; t <= 300; t++) {
            String id = "sin-" + t;
            pedidos.insertOne(new Document("_id", id).append("estado", "PENDIENTE").append("version", 0L).append("historial", new ArrayList<>()));
            List<Boolean> r = Hilos.aLaVez(2, i -> () -> transicionar(id, i == 0 ? "PAGADO" : "CANCELADO", false));
            if (r.get(0) && r.get(1)) imposibles++;
        }
        Salida.linea(R, "  sin versión: en %d de 300 intentos, las dos operaciones respondieron 'hecho' (pagado y cancelado); el estado final depende de quién escribió último".formatted(imposibles));
        assertTrue(imposibles > 0);
        Document ej = pedidos.find(Filters.and(Filters.regex("_id", "^sin-"), Filters.size("historial", 2))).first();
        Salida.linea(R, "  ejemplo de historial imposible: " + ej.toJson());

        int dobles = 0, finalesIncoherentes = 0;
        for (int t = 1; t <= 300; t++) {
            String id = "con-" + t;
            pedidos.insertOne(new Document("_id", id).append("estado", "PENDIENTE").append("version", 0L).append("historial", new ArrayList<>()));
            List<Boolean> r = Hilos.aLaVez(2, i -> () -> transicionar(id, i == 0 ? "PAGADO" : "CANCELADO", true));
            if (r.get(0) && r.get(1)) dobles++;
            Document p = pedidos.find(Filters.eq("_id", id)).first();
            List<?> h = p.getList("historial", Document.class);
            boolean coherente = h.size() == 1 && p.getString("estado").equals(((Document) h.get(0)).getString("estado")) && p.getLong("version") == 1L
                    && (r.get(0) ^ r.get(1));
            if (!coherente) finalesIncoherentes++;
        }
        Salida.linea(R, "  con bloqueo optimista (versión): %d de 300 con las dos respondiendo 'hecho', %d estados incoherentes".formatted(dobles, finalesIncoherentes));
        assertEquals(0, dobles);
        assertEquals(0, finalesIncoherentes);
        // una cadena legal completa sigue funcionando
        pedidos.insertOne(new Document("_id", "cadena").append("estado", "PENDIENTE").append("version", 0L).append("historial", new ArrayList<>()));
        assertTrue(transicionar("cadena", "PAGADO", true));
        assertTrue(transicionar("cadena", "ENVIADO", true));
        assertFalse(transicionar("cadena", "CANCELADO", true), "CANCELADO solo desde PENDIENTE");
    }

    // ---------------------------------------------------------------- 3 y 4. crear pedido en transacción

    static class SinExistencias extends RuntimeException {
        SinExistencias(String p) { super("sin existencias de " + p); }
    }

    /** Crea el pedido y descuenta cada producto. Devuelve cuántas veces se ejecutó el cuerpo (1 = sin reintentos). */
    static int crearPedido(String id, List<String> prods, boolean fallarAlFinal, AtomicInteger ejecuciones) {
        try (ClientSession s = mongo.startSession()) {
            s.withTransaction(() -> {
                ejecuciones.incrementAndGet();
                for (String p : prods) {
                    if (productos.updateOne(s, Filters.and(Filters.eq("_id", p), Filters.gte("existencias", 1)), Updates.inc("existencias", -1)).getModifiedCount() == 0) throw new SinExistencias(p);
                }
                pedidos.insertOne(s, new Document("_id", id).append("estado", "PENDIENTE").append("productos", prods));
                if (fallarAlFinal) throw new IllegalStateException("fallo inyectado antes de confirmar");
                return null;
            }, TX);
        }
        return ejecuciones.get();
    }

    @Test @Order(3)
    void paso3_crearPedidoEnTransaccion() {
        Salida.linea(R, "PASO 3. crear pedido: tres productos, el tercero sin existencias");
        productos.drop(); pedidos.drop();
        productos.insertMany(List.of(new Document("_id", "A").append("existencias", 10), new Document("_id", "B").append("existencias", 10), new Document("_id", "C").append("existencias", 0)));
        // sin transacción: los dos primeros descuentos se quedan
        for (String p : List.of("A", "B", "C")) {
            if (productos.updateOne(Filters.and(Filters.eq("_id", p), Filters.gte("existencias", 1)), Updates.inc("existencias", -1)).getModifiedCount() == 0) break;
        }
        Salida.linea(R, "  sin transacción: A=%d B=%d C=%d y ningún pedido (se descontó sin pedido)".formatted(stock("A"), stock("B"), stock("C")));
        assertEquals(9, stock("A"));
        productos.updateOne(Filters.eq("_id", "A"), Updates.set("existencias", 10));
        productos.updateOne(Filters.eq("_id", "B"), Updates.set("existencias", 10));
        // con transacción
        assertThrows(SinExistencias.class, () -> crearPedido("o1", List.of("A", "B", "C"), false, new AtomicInteger()));
        Salida.linea(R, "  con transacción, tercero sin existencias: A=%d B=%d C=%d, pedidos=%d".formatted(stock("A"), stock("B"), stock("C"), pedidos.countDocuments()));
        assertEquals(10, stock("A")); assertEquals(10, stock("B")); assertEquals(0, pedidos.countDocuments());
        productos.updateOne(Filters.eq("_id", "C"), Updates.set("existencias", 10));
        assertThrows(IllegalStateException.class, () -> crearPedido("o2", List.of("A", "B", "C"), true, new AtomicInteger()));
        Salida.linea(R, "  con transacción, fallo después del tercer descuento: A=%d B=%d C=%d, pedidos=%d".formatted(stock("A"), stock("B"), stock("C"), pedidos.countDocuments()));
        assertEquals(10, stock("A")); assertEquals(10, stock("C")); assertEquals(0, pedidos.countDocuments());
        crearPedido("o3", List.of("A", "B", "C"), false, new AtomicInteger());
        assertEquals(9, stock("C")); assertEquals(1, pedidos.countDocuments());
        Salida.linea(R, "  el camino feliz confirma: A=%d B=%d C=%d, pedidos=%d".formatted(stock("A"), stock("B"), stock("C"), pedidos.countDocuments()));
    }

    static int stock(String id) { return productos.find(Filters.eq("_id", id)).first().getInteger("existencias"); }

    @Test @Order(4)
    void paso4_conflictos() {
        Salida.linea(R, "PASO 4. 200 creaciones concurrentes sobre los mismos dos productos (120 unidades de cada uno)");
        for (int rep = 1; rep <= 3; rep++) {
            productos.drop(); pedidos.drop();
            productos.insertMany(List.of(new Document("_id", "A").append("existencias", 120), new Document("_id", "B").append("existencias", 120)));
            AtomicInteger ejecuciones = new AtomicInteger(), terminados = new AtomicInteger(), rechazados = new AtomicInteger(), otros = new AtomicInteger();
            Hilos.aLaVez(200, i -> () -> {
                try { crearPedido("n" + i, List.of("A", "B"), false, ejecuciones); terminados.incrementAndGet(); }
                catch (SinExistencias e) { rechazados.incrementAndGet(); }
                catch (RuntimeException e) { otros.incrementAndGet(); }
                return null;
            });
            int reintentos = ejecuciones.get() - 200;
            Salida.linea(R, "  ejecución %d: terminaron %d, rechazados por existencias %d, otros errores %d; el cuerpo se ejecutó %d veces (%d repeticiones por conflicto); existencias A=%d B=%d, pedidos=%d"
                    .formatted(rep, terminados.get(), rechazados.get(), otros.get(), ejecuciones.get(), reintentos, stock("A"), stock("B"), pedidos.countDocuments()));
            assertEquals(120, terminados.get());
            assertEquals(80, rechazados.get());
            assertEquals(0, otros.get());
            assertEquals(0, stock("A")); assertEquals(0, stock("B"));
            assertEquals(120, pedidos.countDocuments());
        }
    }

    // ---------------------------------------------------------------- 5. coste

    @Test @Order(5)
    void paso5_coste() {
        Salida.linea(R, "PASO 5. descontar una unidad: actualización condicional frente a transacción de un solo documento");
        productos.drop();
        productos.insertOne(new Document("_id", "X").append("existencias", 1_000_000));
        int n = 3000, calentamiento = 500;
        long[] cond = new long[n], tx = new long[n];
        // las dos variantes se alternan en el mismo bucle: si la máquina va más lenta o más rápida durante la medida, afecta a las dos por igual
        try (ClientSession s = mongo.startSession()) {
            for (int i = 0; i < n + calentamiento; i++) {
                long a = System.nanoTime();
                productos.updateOne(Filters.and(Filters.eq("_id", "X"), Filters.gte("existencias", 1)), Updates.inc("existencias", -1));
                long b = System.nanoTime();
                s.withTransaction(() -> productos.updateOne(s, Filters.and(Filters.eq("_id", "X"), Filters.gte("existencias", 1)), Updates.inc("existencias", -1)), TX);
                long c = System.nanoTime();
                if (i >= calentamiento) { cond[i - calentamiento] = (b - a) / 1000; tx[i - calentamiento] = (c - b) / 1000; }
            }
        }
        double mc = Salida.media(cond), mt = Salida.media(tx);
        Salida.linea(R, "  condicional:          media %.0f us, mediana %d us, p95 %d us (%d operaciones)".formatted(mc, Salida.percentil(cond, 0.5), Salida.percentil(cond, 0.95), n));
        Salida.linea(R, "  transacción 1 doc:    media %.0f us, mediana %d us, p95 %d us (%d operaciones)".formatted(mt, Salida.percentil(tx, 0.5), Salida.percentil(tx, 0.95), n));
        Salida.linea(R, "  la transacción cuesta %.1f veces más en media y %.1f veces más en la mediana cuando no hace falta".formatted(mt / mc, Salida.percentil(tx, 0.5) / (double) Salida.percentil(cond, 0.5)));
        // con margen del 20 %: la medida es de microsegundos y una máquina cargada la desordena; lo normal es que la transacción cueste de 1,2 a 1,5 veces más
        assertTrue(Salida.percentil(tx, 0.5) >= 0.8 * Salida.percentil(cond, 0.5), "la transacción no es más barata que la actualización condicional");

    }
}
