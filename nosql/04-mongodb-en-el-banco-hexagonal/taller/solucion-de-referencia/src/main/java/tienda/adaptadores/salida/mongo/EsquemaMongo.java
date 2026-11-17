package tienda.adaptadores.salida.mongo;

import com.mongodb.client.MongoDatabase;
import org.bson.Document;

/**
 * Colecciones, validador e índices del modelo (taller 01) y de las consultas (taller 02). Las colecciones se crean antes de usarlas
 * en transacciones. El validador impone lo que el modelo promete: líneas de 1 a 50, historial de hasta 20, total y cantidades válidos.
 */
public final class EsquemaMongo {

    public static final int MAXIMO_DE_LINEAS = 50;

    private EsquemaMongo() {
    }

    /** Dos instancias que arrancan a la vez pueden crear la misma colección: la segunda recibe NamespaceExists (48) y no es un error. */
    private static void crearSiNoExiste(MongoDatabase db, String nombre, Runnable crear) {
        try {
            crear.run();
        } catch (com.mongodb.MongoCommandException error) {
            if (error.getErrorCode() != 48) {
                throw error;
            }
        }
    }

    public static void crear(MongoDatabase db) {
        var existentes = db.listCollectionNames().into(new java.util.ArrayList<>());
        for (String nombre : new String[]{"productos", "peticiones", "bandeja", "turnos", "cobros"}) {
            if (!existentes.contains(nombre)) {
                crearSiNoExiste(db, nombre, () -> db.createCollection(nombre));
            }
        }
        if (!existentes.contains("pedidos")) {
            crearSiNoExiste(db, "pedidos", () -> db.runCommand(Document.parse("""
                    { create: "pedidos", validationLevel: "strict", validationAction: "error", validator: { $jsonSchema: {
                      bsonType: "object",
                      required: ["clienteId", "estado", "fecha", "total", "lineas", "historial", "version"],
                      properties: {
                        clienteId: { bsonType: "string", minLength: 1 },
                        estado: { enum: ["REQUIERE_APROBACION", "PENDIENTE", "CONFIRMADO", "CANCELADO"] },
                        total: { bsonType: ["int", "long"], minimum: 0 },
                        lineas: { bsonType: "array", minItems: 1, maxItems: %d, items: { bsonType: "object",
                          required: ["productoId", "cantidad", "precio"],
                          properties: { cantidad: { bsonType: ["int", "long"], minimum: 1 } } } },
                        historial: { bsonType: "array", maxItems: %d }
                      } } } }
                    """.formatted(MAXIMO_DE_LINEAS, PedidosMongo.TOPE_DEL_HISTORIAL))));
        }
        // C3: los pedidos de un cliente, del más nuevo al más viejo, paginados por clave (fecha, _id)
        db.getCollection("pedidos").createIndex(new Document("clienteId", 1).append("fecha", -1).append("_id", -1));
        // C4: los PENDIENTE más viejos que un umbral (la tarea de cancelación)
        db.getCollection("pedidos").createIndex(new Document("estado", 1).append("fecha", 1));
        db.getCollection("bandeja").createIndex(new Document("publicado", 1).append("ocurridoEn", 1));
    }
}
