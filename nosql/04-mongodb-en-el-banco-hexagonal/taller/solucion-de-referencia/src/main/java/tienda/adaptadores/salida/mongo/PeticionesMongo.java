package tienda.adaptadores.salida.mongo;

import static com.mongodb.client.model.Filters.eq;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.util.Date;
import java.util.Optional;
import org.bson.Document;
import tienda.aplicacion.puertos.salida.PeticionesProcesadas;

/** La idempotencia de la transacción: la clave es el {@code _id}, así que dos pedidos con la misma clave no caben. */
public final class PeticionesMongo implements PeticionesProcesadas {

    private final MongoCollection<Document> peticiones;
    private final Op op;

    public PeticionesMongo(MongoDatabase db, UnidadDeTrabajoMongo unidad) {
        this.peticiones = db.getCollection("peticiones");
        this.op = new Op(unidad);
    }

    @Override
    public Optional<String> pedidoDe(String clave) {
        return Errores.proteger(() -> Optional.ofNullable(op.buscar(peticiones, eq("_id", clave)).first()).map(d -> d.getString("pedidoId")));
    }

    @Override
    public void registrar(String clave, String pedidoId) {
        Errores.proteger(() -> op.insertar(peticiones, new Document("_id", clave).append("pedidoId", pedidoId).append("fecha", new Date())));
    }
}
