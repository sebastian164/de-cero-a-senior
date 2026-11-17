package banco.adaptadores.salida.mongo;

import banco.aplicacion.puertos.salida.OrdenesProcesadas;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

/** La clave de la orden como _id: insertar dos veces la misma falla con clave duplicada. */
public final class OrdenesMongo implements OrdenesProcesadas {

    private final MongoCollection<Document> ordenes;

    public OrdenesMongo(MongoDatabase base) {
        this.ordenes = base.getCollection("ordenes_procesadas");
    }

    @Override
    public boolean registrarSiEsNueva(String clave) {
        try {
            ordenes.insertOne(new Document("_id", clave));
            return true;
        } catch (MongoWriteException error) {
            if (error.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                return false;
            }
            throw error;
        }
    }
}
