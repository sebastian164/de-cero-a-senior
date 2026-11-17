package banco.adaptadores.salida.mongo;

import static com.mongodb.client.model.Filters.eq;

import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.puertos.salida.OrdenesProcesadas;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.util.Optional;
import org.bson.Document;

/** Las órdenes atendidas. El _id es la clave de la orden: MongoDB garantiza que no se repite. */
public final class OrdenesMongo implements OrdenesProcesadas {

    private final MongoCollection<Document> ordenes;
    private final UnidadDeTrabajoMongo transacciones;

    public OrdenesMongo(MongoDatabase base, UnidadDeTrabajoMongo transacciones) {
        this.ordenes = base.getCollection(EsquemaMongo.ORDENES);
        this.transacciones = transacciones;
    }

    @Override
    public boolean registrarSiEsNueva(String clave) {
        Optional<ClientSession> sesion = transacciones.sesionEnCurso();
        // Dentro de una transacción, un error de clave duplicada la aborta entera. Por eso primero se mira.
        Document existente = sesion.map(s -> ordenes.find(s, eq("_id", clave)).first())
                .orElseGet(() -> ordenes.find(eq("_id", clave)).first());
        if (existente != null) {
            return false;
        }
        Document orden = new Document("_id", clave);
        try {
            sesion.ifPresentOrElse(s -> ordenes.insertOne(s, orden), () -> ordenes.insertOne(orden));
            return true;
        } catch (MongoWriteException error) {
            if (error.getError().getCategory() != ErrorCategory.DUPLICATE_KEY) {
                throw error;
            }
            if (sesion.isPresent()) {
                throw new ConflictoDeConcurrencia("la orden " + clave);   // que se repita entera: la próxima vez la verá
            }
            return false;                                                 // suelta: otro la registró entre mirar e insertar
        }
    }
}
