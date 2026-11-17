package comun;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.InsertManyOptions;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import org.bson.Document;

/** Genera el volumen realista del servicio de pedidos: clientes, catálogo y pedidos con un cliente muy activo (el 1). */
public final class Carga {
    private Carga() {}

    public static final long TRES_ANOS_MS = 3L * 365 * 24 * 3600 * 1000;
    public static final String[] ESTADOS = {"PENDIENTE", "PAGADO", "ENVIADO", "CANCELADO"};

    public static void clientes(MongoDatabase db, int n) {
        MongoCollection<Document> c = db.getCollection("clientes");
        List<Document> lote = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            lote.add(new Document("_id", i).append("nombre", "Cliente " + i).append("email", "cliente" + i + "@correo" + (i % 50) + ".example")
                    .append("ciudad", "Ciudad " + (i % 200)).append("creado", new Date(System.currentTimeMillis() - (i % 1000) * 86_400_000L)));
            if (lote.size() == 10_000) { c.insertMany(lote, new InsertManyOptions().ordered(false)); lote.clear(); }
        }
        if (!lote.isEmpty()) c.insertMany(lote);
    }

    public static void productos(MongoDatabase db, int n) {
        MongoCollection<Document> c = db.getCollection("productos");
        List<Document> lote = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            lote.add(new Document("_id", i).append("nombre", "Producto " + i).append("precio", 500L + (i * 37L) % 9000).append("existencias", 1_000_000));
        }
        c.insertMany(lote);
    }

    /** El documento de un pedido, con 1 a 5 líneas (copia nombre y precio del producto) y su historial de estados. */
    public static Document pedido(Random r, long id, int clienteId, long fechaMs, int productos) {
        int nLineas = 1 + r.nextInt(5);
        List<Document> lineas = new ArrayList<>();
        long total = 0;
        for (int l = 0; l < nLineas; l++) {
            int p = 1 + r.nextInt(productos);
            int cant = 1 + r.nextInt(4);
            long precio = 500L + (p * 37L) % 9000;
            total += precio * cant;
            lineas.add(new Document("productoId", p).append("nombre", "Producto " + p).append("cantidad", cant).append("precio", precio));
        }
        String estado = ESTADOS[r.nextInt(ESTADOS.length)];
        List<Document> historial = new ArrayList<>();
        historial.add(new Document("estado", "PENDIENTE").append("fecha", new Date(fechaMs)));
        if (!estado.equals("PENDIENTE")) historial.add(new Document("estado", estado).append("fecha", new Date(fechaMs + 3_600_000)));
        return new Document("_id", id).append("clienteId", clienteId).append("estado", estado).append("fecha", new Date(fechaMs))
                .append("total", total).append("lineas", lineas).append("historial", historial).append("version", 0L);
    }

    /**
     * Inserta los pedidos: el cliente 1 recibe {@code deActivo}, el resto se reparte al azar. Las fechas van sobre tres años,
     * con empates a propósito (la mitad de los pedidos del cliente activo comparten fecha por pares).
     */
    public static void pedidos(MongoCollection<Document> c, long total, int clientes, int productos, long deActivo) {
        Random r = new Random(42);
        long ahora = System.currentTimeMillis();
        List<Document> lote = new ArrayList<>();
        for (long id = 1; id <= total; id++) {
            int cliente = id <= deActivo ? 1 : 2 + r.nextInt(clientes - 1);
            long fecha = ahora - (long) (r.nextDouble() * TRES_ANOS_MS);
            if (cliente == 1) fecha = (fecha / 60_000) * 60_000;   // minutos redondos: aparecen empates de fecha
            lote.add(pedido(r, id, cliente, fecha, productos));
            if (lote.size() == 10_000) { c.insertMany(lote, new InsertManyOptions().ordered(false)); lote.clear(); }
        }
        if (!lote.isEmpty()) c.insertMany(lote, new InsertManyOptions().ordered(false));
    }
}
