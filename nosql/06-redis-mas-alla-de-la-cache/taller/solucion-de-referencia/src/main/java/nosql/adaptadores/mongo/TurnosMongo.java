package nosql.adaptadores.mongo;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.lte;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import nosql.aplicacion.RegistroDeTurnos;
import nosql.aplicacion.TurnoCaducado;
import org.bson.Document;
import tienda.adaptadores.salida.mongo.Errores;
import tienda.adaptadores.salida.mongo.Op;
import tienda.adaptadores.salida.mongo.UnidadDeTrabajoMongo;

/**
 * Los turnos viven en la colección {@code turnos}, en MongoDB: dos documentos por tarea. {@code <tarea>:emitido} cuenta los turnos que se
 * han dado (fuera de transacciones: es un contador) y {@code <tarea>:aplicado} guarda el mayor turno que ha escrito, dentro de la
 * transacción de cada cancelación. Si el cerrojo (Redis) se pierde, el contador no.
 */
public final class TurnosMongo implements RegistroDeTurnos {

    private final MongoCollection<Document> turnos;
    private final Op op;

    public TurnosMongo(MongoDatabase db, UnidadDeTrabajoMongo unidad) {
        this.turnos = db.getCollection("turnos");
        this.op = new Op(unidad);
    }

    @Override
    public long siguiente(String tarea) {
        return Errores.proteger(() -> turnos.findOneAndUpdate(eq("_id", tarea + ":emitido"), Updates.inc("turno", 1L),
                new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)).getLong("turno"));
    }

    @Override
    public void exigir(String tarea, long turno) {
        try {
            // el turno solo puede subir: si ya hay uno mayor, el filtro no encaja y el upsert choca con el _id existente
            op.actualizar(turnos, and(eq("_id", tarea + ":aplicado"), lte("turno", turno)), Updates.set("turno", turno), new UpdateOptions().upsert(true));
        } catch (MongoWriteException error) {
            if (error.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                throw new TurnoCaducado(tarea, turno);
            }
            throw Errores.traducir(error);
        } catch (RuntimeException error) {
            throw Errores.traducir(error);
        }
    }
}
