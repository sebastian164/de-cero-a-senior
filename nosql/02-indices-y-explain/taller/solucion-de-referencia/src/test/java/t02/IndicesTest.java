package t02;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import comun.Carga;
import comun.Entorno;
import comun.Hilos;
import comun.Salida;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bson.Document;
import org.junit.jupiter.api.*;

/** Taller 02: explain antes y después, índices con ESR, paginación por clave, coste de escribir, $indexStats, consulta cubierta. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IndicesTest {
    static final String R = "resultados.txt";
    static final int CLIENTES = 20_000, PRODUCTOS = 5_000;
    static final long PEDIDOS = 300_000, ACTIVO = 12_000;
    static MongoClient mongo;
    static MongoDatabase db;
    static MongoCollection<Document> pedidos;

    record Consulta(String nombre, String justificacion, Document filtro, Document orden, int limite, boolean frecuente) {}

    static List<Consulta> consultas() {
        Date hace24h = new Date(System.currentTimeMillis() - 24 * 3600 * 1000L);
        return List.of(
            new Consulta("Q1 mis pedidos, primera página", "GET /clientes/{id}/pedidos", new Document("clienteId", 1), new Document("fecha", -1).append("_id", -1), 20, true),
            new Consulta("Q2 pendientes de más de 24 h", "tarea de cancelación", new Document("estado", "PENDIENTE").append("fecha", new Document("$lt", hace24h)), new Document("fecha", 1), 100, true),
            new Consulta("Q3 pedido por _id", "GET /pedidos/{id}", new Document("_id", 150_000L), null, 0, true),
            new Consulta("Q4 mis pedidos pagados de más de 200 euros", "pantalla de historial con filtros", new Document("clienteId", 1).append("estado", "PAGADO").append("total", new Document("$gte", 20_000L)), new Document("fecha", -1), 20, true));
    }

    static Document explain(Document find) {
        Document cmd = new Document("explain", find).append("verbosity", "executionStats");
        return db.runCommand(cmd);
    }

    static Document findCmd(Consulta q) {
        Document f = new Document("find", "pedidos").append("filter", q.filtro());
        if (q.orden() != null) f.append("sort", q.orden());
        if (q.limite() > 0) f.append("limit", q.limite());
        return f;
    }

    static void etapas(Object plan, List<String> out) {
        if (plan instanceof Document d) {
            if (d.containsKey("stage")) out.add(d.getString("stage") + (d.containsKey("indexName") ? "(" + d.getString("indexName") + ")" : ""));
            for (Object v : d.values()) etapas(v, out);
        } else if (plan instanceof List<?> l) for (Object o : l) etapas(o, out);
    }

    /** Devuelve {keys, docs, returned} y escribe la línea del explain. */
    static long[] resumen(String titulo, Document e) {
        Document st = e.get("executionStats", Document.class);
        List<String> etapas = new ArrayList<>();
        etapas(e.get("queryPlanner", Document.class).get("winningPlan"), etapas);
        long keys = ((Number) st.get("totalKeysExamined")).longValue(), docs = ((Number) st.get("totalDocsExamined")).longValue(), ret = ((Number) st.get("nReturned")).longValue();
        Salida.linea(R, "  %-48s etapas %-62s claves %7d  documentos %7d  devueltos %5d  %d ms".formatted(titulo, etapas, keys, docs, ret, ((Number) st.get("executionTimeMillis")).longValue()));
        return new long[]{keys, docs, ret};
    }

    @BeforeAll
    static void preparar() {
        Salida.limpiar(R);
        mongo = Entorno.mongo();
        db = mongo.getDatabase("t02");
        db.drop();
        pedidos = db.getCollection("pedidos");
        Carga.clientes(db, CLIENTES);
        Carga.pedidos(pedidos, PEDIDOS, CLIENTES, PRODUCTOS, ACTIVO);
        Salida.linea(R, "datos: %d pedidos, el cliente 1 con %d".formatted(PEDIDOS, ACTIVO));
    }

    @AfterAll
    static void cerrar() { db.drop(); mongo.close(); }

    @Test @Order(1)
    void paso1_sinIndices() {
        Salida.linea(R, "PASO 1. explain sin más índices que _id");
        for (Consulta q : consultas()) {
            long[] r = resumen(q.nombre(), explain(findCmd(q)));
            if (!q.nombre().startsWith("Q3")) assertEquals(PEDIDOS, r[1], q.nombre() + " recorre toda la colección");
        }
    }

    @Test @Order(2)
    void paso2y3_indicesConESR() {
        Salida.linea(R, "PASO 2. índices (ESR: igualdad, orden, rango)");
        pedidos.createIndex(new Document("clienteId", 1).append("fecha", -1).append("_id", -1));
        Salida.linea(R, "  I1 {clienteId:1, fecha:-1, _id:-1}: Q1 (igualdad clienteId; orden por fecha y _id para el desempate) y paso 4");
        pedidos.createIndex(new Document("estado", 1).append("fecha", 1));
        Salida.linea(R, "  I2 {estado:1, fecha:1}: Q2 (igualdad estado, el rango y el orden comparten fecha)");
        pedidos.createIndex(new Document("clienteId", 1).append("estado", 1).append("fecha", -1).append("total", 1));
        Salida.linea(R, "  I3 {clienteId:1, estado:1, fecha:-1, total:1}: Q4 (igualdad clienteId y estado, orden fecha, rango total al final)");
        pedidos.createIndex(new Document("total", 1));
        Salida.linea(R, "  I4 {total:1}: 'por si acaso hay un informe por importe' (el paso 6 mostrará que nadie lo usa)");
        Salida.linea(R, "PASO 3. explain con índices");
        for (Consulta q : consultas()) {
            long[] r = resumen(q.nombre(), explain(findCmd(q)));
            double proporcion = Math.max(r[0], r[1]) / (double) Math.max(1, r[2]);
            Salida.linea(R, "     proporción examinados/devueltos = %.2f".formatted(proporcion));
            assertTrue(proporcion <= 2.1 || q.nombre().startsWith("Q4"), q.nombre() + " debe examinar lo que devuelve");
            assertTrue(r[1] <= Math.max(r[2], 1), q.nombre() + ": nunca más documentos que los devueltos");
        }
        // Q4: ejecutar la consulta con la proporción de claves y mostrar por qué (el rango de total se filtra dentro del índice)
        Document sinTotalEnIndice = explain(findCmd(consultas().get(3)).append("hint", "clienteId_1_fecha_-1__id_-1"));
        resumen("Q4 forzando I1 (sin total en el índice)", sinTotalEnIndice);
    }

    @Test @Order(3)
    void paso4_paginacionPorClave() throws Exception {
        Salida.linea(R, "PASO 4. paginación por clave con desempate (fecha, _id)");
        int tam = 20;
        // coste: la página 500 por clave cuesta lo mismo que la 1
        List<Document> pag1 = pagina(1, null, tam);
        Document cursor = pag1.get(pag1.size() - 1);
        for (int p = 2; p <= 500; p++) { List<Document> pg = pagina(1, cursor, tam); cursor = pg.get(pg.size() - 1); }
        Document e1 = explain(paginaCmd(1, null, tam));
        Document e500 = explain(paginaCmd(1, cursor, tam));
        long[] a = resumen("página 1 (por clave)", e1), b = resumen("página 500 (por clave)", e500);
        assertTrue(Math.abs(a[1] - b[1]) <= 1 && Math.abs(a[0] - b[0]) <= 2, "la página 500 cuesta lo mismo que la 1");
        Document skip = findCmd(consultas().get(0)).append("skip", 499 * tam);
        long[] c = resumen("página 500 con skip (la alternativa)", explain(skip));
        assertTrue(c[0] > 400 * tam, "con skip el coste crece con la página");

        // estabilidad: recorrer todo mientras otro hilo inserta pedidos del mismo cliente (al principio, en medio y con fechas repetidas)
        Set<Long> existentes = new HashSet<>();
        pedidos.find(Filters.eq("clienteId", 1)).projection(new Document("_id", 1)).forEach(d -> existentes.add(d.getLong("_id")));
        long antes = System.currentTimeMillis();
        List<Document> viejos = pedidos.find(Filters.eq("clienteId", 1)).sort(Sorts.descending("fecha")).limit(300).into(new ArrayList<>());
        AtomicBoolean seguir = new AtomicBoolean(true);
        long[] insertados = {0};
        Thread escritor = new Thread(() -> {
            Random r = new Random(5);
            long id = 5_000_000;
            while (seguir.get()) {
                id++;
                Date fecha = switch ((int) (id % 3)) {
                    case 0 -> new Date(System.currentTimeMillis());                       // al principio del orden descendente
                    case 1 -> viejos.get(r.nextInt(viejos.size())).getDate("fecha");      // mismo instante que un pedido existente (empate)
                    default -> new Date(antes - (long) (r.nextDouble() * Carga.TRES_ANOS_MS)); // en medio
                };
                pedidos.insertOne(Carga.pedido(r, id, 1, fecha.getTime(), PRODUCTOS));
                insertados[0]++;
            }
        });
        escritor.start();
        List<Long> vistos = new ArrayList<>();
        Document cur = null;
        int paginas = 0;
        while (true) {
            List<Document> pg = pagina(1, cur, tam);
            if (pg.isEmpty()) break;
            for (Document d : pg) vistos.add(d.getLong("_id"));
            cur = pg.get(pg.size() - 1);
            paginas++;
            Hilos.dormir(2);
        }
        seguir.set(false);
        escritor.join();
        Set<Long> unicos = new HashSet<>(vistos);
        Set<Long> perdidos = new HashSet<>(existentes); perdidos.removeAll(unicos);
        Salida.linea(R, "  por clave: %d páginas, %d pedidos vistos, %d repetidos, %d de los %d existentes al empezar perdidos; %d insertados durante el recorrido"
                .formatted(paginas, vistos.size(), vistos.size() - unicos.size(), perdidos.size(), existentes.size(), insertados[0]));
        assertEquals(vistos.size(), unicos.size(), "ningún pedido se repite");
        assertTrue(perdidos.isEmpty(), "ningún pedido existente se pierde");
        assertTrue(insertados[0] > 100, "el escritor insertó durante el recorrido");

        // la alternativa con skip/limit, con el mismo escritor, repite y pierde
        seguir.set(true);
        long[] ins2 = {0};
        Thread escritor2 = new Thread(() -> {
            Random r = new Random(6);
            long id = 7_000_000;
            while (seguir.get()) { pedidos.insertOne(Carga.pedido(r, ++id, 1, System.currentTimeMillis(), PRODUCTOS)); ins2[0]++; }
        });
        escritor2.start();
        List<Long> vistos2 = new ArrayList<>();
        for (int p = 0; ; p++) {
            List<Document> pg = pedidos.find(Filters.eq("clienteId", 1)).sort(new Document("fecha", -1).append("_id", -1)).skip(p * tam).limit(tam).into(new ArrayList<>());
            if (pg.isEmpty() || p > 700) break;
            for (Document d : pg) vistos2.add(d.getLong("_id"));
            Hilos.dormir(2);
        }
        seguir.set(false);
        escritor2.join();
        long repetidos = vistos2.size() - new HashSet<>(vistos2).size();
        Salida.linea(R, "  con skip: %d pedidos vistos, %d repetidos con %d inserciones al principio".formatted(vistos2.size(), repetidos, ins2[0]));
        assertTrue(repetidos > 0, "skip/limit repite pedidos cuando se inserta al principio");
    }

    static Document paginaCmd(int cliente, Document cursor, int tam) {
        Document filtro = new Document("clienteId", cliente);
        if (cursor != null) {
            Date f = cursor.getDate("fecha");
            Long id = cursor.getLong("_id");
            filtro.append("$or", List.of(new Document("fecha", new Document("$lt", f)),
                    new Document("fecha", f).append("_id", new Document("$lt", id))));
        }
        return new Document("find", "pedidos").append("filter", filtro).append("sort", new Document("fecha", -1).append("_id", -1)).append("limit", tam);
    }

    static List<Document> pagina(int cliente, Document cursor, int tam) {
        Document cmd = paginaCmd(cliente, cursor, tam);
        return pedidos.find((Document) cmd.get("filter")).sort((Document) cmd.get("sort")).limit(tam)
                .projection(new Document("fecha", 1)).into(new ArrayList<>());
    }

    @Test @Order(4)
    void paso5_costeDeEscribir() {
        Salida.linea(R, "PASO 5. insertar 100.000 pedidos con 1, 3 y 6 índices (mejor de 3 ejecuciones)");
        List<List<Document>> juegos = List.of(
            List.of(new Document("clienteId", 1).append("fecha", -1)),
            List.of(new Document("clienteId", 1).append("fecha", -1), new Document("estado", 1).append("fecha", 1), new Document("total", 1)),
            List.of(new Document("clienteId", 1).append("fecha", -1), new Document("estado", 1).append("fecha", 1), new Document("total", 1),
                    new Document("lineas.productoId", 1).append("fecha", -1), new Document("historial.fecha", 1), new Document("clienteId", 1).append("estado", 1).append("fecha", -1)));
        long[] ms = new long[3];
        for (int j = 0; j < 3; j++) {
            long[] t = new long[3];
            for (int rep = 0; rep < 3; rep++) {
                MongoCollection<Document> c = db.getCollection("escritura");
                c.drop();
                for (Document ix : juegos.get(j)) c.createIndex(ix);
                long a = System.nanoTime();
                Carga.pedidos(c, 100_000, CLIENTES, PRODUCTOS, 100);
                t[rep] = (System.nanoTime() - a) / 1_000_000;
            }
            Arrays.sort(t);
            ms[j] = t[0];   // el mínimo de tres: la ejecución menos perturbada por el resto de la máquina
            int n = juegos.get(j).size() + 1; // + _id
            Salida.linea(R, "  %d índices (+ _id): %d ms para 100.000 pedidos (%.0f pedidos/s)".formatted(n - 1, ms[j], 100_000.0 / ms[j] * 1000));
        }
        assertTrue(ms[2] > ms[0] * 1.3 && ms[1] >= ms[0] * 0.9, "más índices, más coste de escritura");
    }

    @Test @Order(5)
    void paso6_indicesSinUso() {
        Salida.linea(R, "PASO 6. $indexStats tras la carga de consultas");
        // la carga de consultas: las cuatro, varias veces
        for (int i = 0; i < 20; i++) for (Consulta q : consultas()) {
            var c = pedidos.find(new Document(q.filtro()));
            if (q.orden() != null) c.sort(q.orden());
            if (q.limite() > 0) c.limit(q.limite());
            c.into(new ArrayList<>());
        }
        List<String> sinUso = new ArrayList<>();
        for (Document d : pedidos.aggregate(List.of(new Document("$indexStats", new Document())))) {
            long ops = ((Number) d.get("accesses", Document.class).get("ops")).longValue();
            Salida.linea(R, "  índice %-40s accesos %d".formatted(d.getString("name"), ops));
            if (ops == 0 && !d.getString("name").equals("_id_")) sinUso.add(d.getString("name"));
        }
        assertEquals(List.of("total_1"), sinUso, "solo I4 no se usa");
        pedidos.dropIndex("total_1");
        Salida.linea(R, "  eliminado: total_1 (ninguna consulta de la carga lo usa; cuesta escritura y memoria)");
    }

    @Test @Order(6)
    void paso7_consultaQueNingunIndicePuedeResolver() {
        Salida.linea(R, "PASO 7. expresión regular que empieza por .*");
        MongoCollection<Document> clientes = db.getCollection("clientes");
        clientes.createIndex(new Document("email", 1));
        Document ancla = explain(new Document("find", "clientes").append("filter", Document.parse("{email: {$regex: '^cliente4999@'}}")));
        Document sin = explain(new Document("find", "clientes").append("filter", Document.parse("{email: {$regex: '.*cliente4999@'}}")));
        long[] a = resumen("regex anclada ^cliente4999@", ancla);
        long[] b = resumen("regex .*cliente4999@", sin);
        assertTrue(a[0] <= 5);
        assertTrue(b[0] >= CLIENTES, "recorre las claves de todo el índice");
        assertTrue(b[2] <= 2);
    }

    @Test @Order(7)
    void paso8_consultaCubierta() {
        Salida.linea(R, "PASO 8. consulta cubierta por I1");
        Document cmd = new Document("find", "pedidos").append("filter", new Document("clienteId", 1)).append("projection", new Document("_id", 0).append("clienteId", 1).append("fecha", 1))
                .append("sort", new Document("fecha", -1)).append("limit", 100);
        Document e = explain(cmd);
        long[] r = resumen("clienteId, fecha de los últimos 100 pedidos", e);
        List<String> et = new ArrayList<>();
        etapas(e.get("queryPlanner", Document.class).get("winningPlan"), et);
        assertEquals(0, r[1], "totalDocsExamined: 0");
        assertTrue(et.contains("PROJECTION_COVERED"));
    }
}
