package nosql.adaptadores.mongo;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.util.Date;
import java.util.UUID;
import nosql.aplicacion.PasarelaDePagos;
import org.bson.Document;
import tienda.adaptadores.salida.mongo.Errores;
import tienda.dominio.Dinero;

/**
 * El servicio de pagos simulado: cobra de verdad (apunta en su propia base, fuera de la transacción de los pedidos) y puede o no
 * deduplicar por la clave que le envían.
 */
public final class PasarelaMongo implements PasarelaDePagos {

    private final MongoCollection<Document> cobros;
    private final boolean idempotente;

    public PasarelaMongo(MongoDatabase pagos, boolean idempotente) {
        this.cobros = pagos.getCollection("cobros");
        this.idempotente = idempotente;
    }

    @Override
    public void cobrar(String claveDeIdempotencia, String cliente, Dinero importe) {
        String id = idempotente && claveDeIdempotencia != null ? claveDeIdempotencia : UUID.randomUUID().toString();
        try {
            cobros.insertOne(new Document("_id", id).append("cliente", cliente).append("importe", importe.centavos()).append("fecha", new Date()));
        } catch (MongoWriteException error) {
            if (error.getError().getCategory() != ErrorCategory.DUPLICATE_KEY) {
                throw Errores.traducir(error);
            }
        }
    }

    public long cobros() {
        return cobros.countDocuments();
    }

    public long importeTotal() {
        long total = 0;
        for (Document d : cobros.find()) {
            total += d.getLong("importe");
        }
        return total;
    }
}
