package nosql.adaptadores.mongo;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.lt;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import nosql.aplicacion.PedidosAbandonados;
import org.bson.Document;
import tienda.adaptadores.salida.mongo.Errores;

/** C4: los PENDIENTE más viejos que un límite. Usa el índice {estado, fecha}: examina lo que devuelve. */
public final class AbandonadosMongo implements PedidosAbandonados {

    private final MongoCollection<Document> pedidos;

    public AbandonadosMongo(MongoDatabase db) {
        this.pedidos = db.getCollection("pedidos");
    }

    @Override
    public List<String> idsPendientesAnterioresA(Instant limite, int max) {
        return Errores.proteger(() -> {
            List<String> ids = new ArrayList<>();
            pedidos.find(and(eq("estado", "PENDIENTE"), lt("fecha", Date.from(limite)))).sort(Sorts.ascending("fecha")).limit(max)
                    .projection(Projections.include("_id")).forEach(d -> ids.add(d.getString("_id")));
            return ids;
        });
    }
}
