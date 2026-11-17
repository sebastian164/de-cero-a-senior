package nosql.adaptadores.mongo;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.lt;
import static com.mongodb.client.model.Filters.or;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Sorts;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import nosql.aplicacion.HistorialDePedidos;
import nosql.aplicacion.ListarPedidosPaginados.Pagina;
import org.bson.Document;
import tienda.adaptadores.salida.mongo.Errores;
import tienda.aplicacion.VistaDePedido;

/** C3: los pedidos de un cliente, paginados por clave (fecha, _id). Cada página cuesta lo mismo que la primera: el índice salta al cursor. */
public final class HistorialMongo implements HistorialDePedidos {

    private final MongoCollection<Document> pedidos;

    public HistorialMongo(MongoDatabase db) {
        this.pedidos = db.getCollection("pedidos");
    }

    @Override
    public Pagina pagina(String cliente, String despuesDe, int tamano) {
        return Errores.proteger(() -> {
            var filtro = eq("clienteId", cliente);
            if (despuesDe != null) {
                String[] cursor = despuesDe.split("\\|", 2);
                Date fecha = new Date(Long.parseLong(cursor[0]));
                filtro = and(filtro, or(lt("fecha", fecha), and(eq("fecha", fecha), lt("_id", cursor[1]))));
            }
            List<Document> docs = pedidos.find(filtro).sort(Sorts.descending("fecha", "_id")).limit(tamano).into(new ArrayList<>());
            List<VistaDePedido> vistas = docs.stream().map(HistorialMongo::aVista).toList();
            String siguiente = null;
            if (docs.size() == tamano) {
                Document ultimo = docs.get(docs.size() - 1);
                siguiente = ultimo.getDate("fecha").getTime() + "|" + ultimo.getString("_id");
            }
            return new Pagina(vistas, siguiente);
        });
    }

    private static VistaDePedido aVista(Document d) {
        return new VistaDePedido(d.getString("_id"), d.getString("clienteId"), d.getString("estado"), d.getLong("total"),
                d.getList("lineas", Document.class).stream().map(l -> new VistaDePedido.Linea(l.getString("productoId"), l.getInteger("cantidad"), l.getLong("precio"))).toList());
    }
}
