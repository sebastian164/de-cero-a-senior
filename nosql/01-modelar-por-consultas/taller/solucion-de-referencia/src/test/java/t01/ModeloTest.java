package t01;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.ValidationOptions;
import comun.Carga;
import comun.Entorno;
import comun.Salida;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Random;
import org.bson.Document;
import org.junit.jupiter.api.*;

/** Taller 01: el modelo de pedidos con volumen real, sus tamaños, sus consultas, el antipatrón y el validador. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ModeloTest {
    static final String R = "resultados.txt";
    static final int CLIENTES = 50_000, PRODUCTOS = 5_000;
    static final long PEDIDOS = Long.getLong("t01.pedidos", 1_000_000L);
    static final long ACTIVO = 20_000;
    static MongoClient mongo;
    static MongoDatabase db;
    static MongoCollection<Document> pedidos;
    static double mediaPedido;

    static Document validador() {
        return Document.parse("""
            {"$jsonSchema": {"bsonType": "object", "required": ["clienteId", "estado", "fecha", "total", "lineas"],
              "properties": {
                "clienteId": {"bsonType": "int"},
                "estado": {"enum": ["PENDIENTE", "PAGADO", "ENVIADO", "CANCELADO"]},
                "fecha": {"bsonType": "date"},
                "total": {"bsonType": ["int", "long"], "minimum": 0},
                "lineas": {"bsonType": "array", "minItems": 1, "maxItems": 50,
                  "items": {"bsonType": "object", "required": ["productoId", "cantidad", "precio"],
                    "properties": {"cantidad": {"bsonType": "int", "minimum": 1}, "precio": {"bsonType": ["int", "long"], "minimum": 0}}}},
                "historial": {"bsonType": "array", "maxItems": 20}}}}""");
    }

    @BeforeAll
    static void preparar() {
        Salida.limpiar(R);
        mongo = Entorno.mongo();
        db = mongo.getDatabase("t01");
        db.drop();
        db.createCollection("pedidos", new CreateCollectionOptions()
                .validationOptions(new ValidationOptions().validator(validador())));
        pedidos = db.getCollection("pedidos");
    }

    @AfterAll
    static void cerrar() { db.drop(); mongo.close(); }

    @Test @Order(1)
    void paso4_cargaYTamanios() {
        long t0 = System.nanoTime();
        Carga.clientes(db, CLIENTES);
        Carga.productos(db, PRODUCTOS);
        Carga.pedidos(pedidos, PEDIDOS, CLIENTES, PRODUCTOS, ACTIVO);
        Salida.linea(R, "carga: %d clientes, %d productos, %d pedidos (el cliente 1 con %d) en %d s"
                .formatted(CLIENTES, PRODUCTOS, PEDIDOS, ACTIVO, (System.nanoTime() - t0) / 1_000_000_000));
        assertEquals(PEDIDOS, pedidos.countDocuments());
        for (String col : List.of("clientes", "productos", "pedidos")) {
            Document r = db.getCollection(col).aggregate(List.of(Document.parse(
                    "{$group: {_id: null, media: {$avg: {$bsonSize: '$$ROOT'}}, max: {$max: {$bsonSize: '$$ROOT'}}}}"))).first();
            Salida.linea(R, "$bsonSize %-9s media %.0f bytes, máximo %d bytes".formatted(col, ((Number) r.get("media")).doubleValue(), ((Number) r.get("max")).intValue()));
            assertTrue(r.getInteger("max") < 16 * 1024, col + ": ningún documento debería pasar de 16 KB");
            if (col.equals("pedidos")) mediaPedido = ((Number) r.get("media")).doubleValue();
        }
        Document lin = pedidos.aggregate(List.of(Document.parse("{$group: {_id: null, lineas: {$max: {$size: '$lineas'}}, historial: {$max: {$size: '$historial'}}}}"))).first();
        Salida.linea(R, "arreglos: máximo de líneas por pedido " + lin.get("lineas") + " (tope del validador 50), máximo del historial " + lin.get("historial") + " (tope 20)");
        assertTrue(lin.getInteger("lineas") <= 50 && lin.getInteger("historial") <= 20, "ningún arreglo sin tope");
        Salida.linea(R, "pedidos del cliente 1 (el activo): " + pedidos.countDocuments(Filters.eq("clienteId", 1)));
    }

    @Test @Order(2)
    void paso5_consultasFrecuentes() {
        pedidos.createIndex(Indexes.compoundIndex(Indexes.ascending("clienteId"), Indexes.descending("fecha")));
        pedidos.createIndex(Indexes.compoundIndex(Indexes.ascending("estado"), Indexes.ascending("fecha")));
        Date hace24h = new Date(System.currentTimeMillis() - 24 * 3600 * 1000L);
        medir("1. pedido por _id", () -> pedidos.find(Filters.eq("_id", 777_777L)).first());
        medir("2. últimos 20 pedidos del cliente activo", () -> pedidos.find(Filters.eq("clienteId", 1)).sort(Sorts.descending("fecha")).limit(20).into(new ArrayList<>()));
        medir("3. 100 pedidos PENDIENTE de más de 24 h", () -> pedidos.find(Filters.and(Filters.eq("estado", "PENDIENTE"), Filters.lt("fecha", hace24h))).sort(Sorts.ascending("fecha")).limit(100).into(new ArrayList<>()));
    }

    static void medir(String nombre, Runnable q) {
        q.run();
        long[] t = new long[30];
        for (int i = 0; i < t.length; i++) { long a = System.nanoTime(); q.run(); t[i] = (System.nanoTime() - a) / 1000; }
        Salida.linea(R, "consulta %-44s media %6.2f ms   p95 %6.2f ms (30 ejecuciones, con índice)".formatted(nombre, Salida.media(t) / 1000, Salida.percentil(t, 0.95) / 1000.0));
        assertTrue(Salida.percentil(t, 0.95) < 100_000, nombre + " debe responder en menos de 100 ms con el volumen completo");
    }

    @Test @Order(3)
    void paso6_elAntipatronDelClienteConPedidosDentro() {
        MongoCollection<Document> anti = db.getCollection("clientes_anti");
        anti.insertOne(new Document("_id", 1).append("nombre", "Cliente activo").append("pedidos", new ArrayList<>()));
        List<Document> muestra = pedidos.find(Filters.eq("clienteId", 1)).limit(2000).into(new ArrayList<>());
        double tam = muestra.stream().mapToInt(d -> org.bson.RawBsonDocument.parse(d.toJson()).getByteBuffer().remaining()).average().orElseThrow();
        // dentro del cliente el pedido no repite su clienteId ni su _id de colección, pero sí lo demás: usamos el tamaño medio medido de los pedidos
        long prediccion = (long) (16L * 1024 * 1024 / tam);
        Salida.linea(R, "antipatrón: tamaño medio de un pedido del cliente activo %.0f bytes -> límite previsto %d pedidos dentro del cliente (16 MiB)".formatted(tam, prediccion));
        long metidos = 0;
        boolean fallo = false;
        Random r = new Random(7);
        long id = 0;
        while (!fallo && metidos < prediccion * 2) {
            List<Document> lote = new ArrayList<>();
            for (int i = 0; i < 100; i++) lote.add(Carga.pedido(r, ++id, 1, System.currentTimeMillis() - i * 60_000L, PRODUCTOS));
            try {
                anti.updateOne(Filters.eq("_id", 1), new Document("$push", new Document("pedidos", new Document("$each", lote))));
                metidos += 100;
            } catch (MongoWriteException e) {
                fallo = true;
                Salida.linea(R, "antipatrón: falló tras %d pedidos con el código %d: %s".formatted(metidos, e.getError().getCode(), e.getError().getMessage()));
                assertTrue(e.getError().getMessage().toLowerCase().contains("larger") || e.getError().getCode() == 10334 || e.getError().getCode() == 17419);
            }
        }
        assertTrue(fallo, "el cliente tiene que llegar al límite de 16 MiB");
        double desvio = Math.abs(metidos - prediccion) / (double) prediccion;
        Salida.linea(R, "antipatrón: previsto %d, real %d (desvío %.1f %%)".formatted(prediccion, metidos, desvio * 100));
        assertTrue(desvio < 0.35, "la predicción por tamaño medio debe acertar con holgura");
        // la ventaja del modelo con referencias: el cliente activo tiene ACTIVO pedidos, cada uno en su documento
        Salida.linea(R, "modelo elegido: el mismo cliente tiene %d pedidos en %d documentos de ~%.0f bytes".formatted(ACTIVO, ACTIVO, mediaPedido));
    }

    @Test @Order(4)
    void paso7_elValidadorRechazaLoMalFormado() {
        Random r = new Random(1);
        Document sinCliente = Carga.pedido(r, 9_000_001L, 1, System.currentTimeMillis(), PRODUCTOS);
        sinCliente.remove("clienteId");
        Document totalNegativo = Carga.pedido(r, 9_000_002L, 1, System.currentTimeMillis(), PRODUCTOS).append("total", -5L);
        Document sinLineas = Carga.pedido(r, 9_000_003L, 1, System.currentTimeMillis(), PRODUCTOS).append("lineas", new ArrayList<>());
        Document estadoRaro = Carga.pedido(r, 9_000_004L, 1, System.currentTimeMillis(), PRODUCTOS).append("estado", "PERDIDO");
        for (Document malo : Arrays.asList(sinCliente, totalNegativo, sinLineas, estadoRaro)) {
            MongoWriteException e = assertThrows(MongoWriteException.class, () -> pedidos.insertOne(malo));
            Salida.linea(R, "validador: rechazado _id=%s con el código %d (%s)".formatted(malo.get("_id"), e.getError().getCode(), e.getError().getMessage()));
            assertEquals(121, e.getError().getCode());
        }
        pedidos.insertOne(Carga.pedido(r, 9_000_005L, 1, System.currentTimeMillis(), PRODUCTOS));
        Salida.linea(R, "validador: un pedido bien formado se acepta");
        // con 51 líneas, el tope de 50 lo rechaza: ningún arreglo crece sin tope
        Document cincuentaYUna = Carga.pedido(r, 9_000_006L, 1, System.currentTimeMillis(), PRODUCTOS);
        List<Document> muchas = new ArrayList<>();
        for (int i = 0; i < 51; i++) muchas.add(new Document("productoId", 1).append("cantidad", 1).append("precio", 100L));
        cincuentaYUna.append("lineas", muchas);
        assertThrows(MongoWriteException.class, () -> pedidos.insertOne(cincuentaYUna));
        Salida.linea(R, "validador: 51 líneas rechazadas (maxItems 50)");
    }
}
