package tienda.adaptadores.salida.mongo;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.conversions.Bson;

/** Cada operación usa la sesión de la transacción en curso, si la hay. */
public final class Op {

    private final UnidadDeTrabajoMongo unidad;

    public Op(UnidadDeTrabajoMongo unidad) {
        this.unidad = unidad;
    }

    public void insertar(MongoCollection<Document> c, Document d) {
        unidad.sesionEnCurso().ifPresentOrElse(s -> c.insertOne(s, d), () -> c.insertOne(d));
    }

    public UpdateResult actualizar(MongoCollection<Document> c, Bson filtro, Bson cambio, UpdateOptions o) {
        return unidad.sesionEnCurso().map(s -> c.updateOne(s, filtro, cambio, o)).orElseGet(() -> c.updateOne(filtro, cambio, o));
    }

    public Document modificar(MongoCollection<Document> c, Bson filtro, Bson cambio, FindOneAndUpdateOptions o) {
        return unidad.sesionEnCurso().map(s -> c.findOneAndUpdate(s, filtro, cambio, o)).orElseGet(() -> c.findOneAndUpdate(filtro, cambio, o));
    }

    public FindIterable<Document> buscar(MongoCollection<Document> c, Bson filtro) {
        return unidad.sesionEnCurso().map(s -> c.find(s, filtro)).orElseGet(() -> c.find(filtro));
    }
}
