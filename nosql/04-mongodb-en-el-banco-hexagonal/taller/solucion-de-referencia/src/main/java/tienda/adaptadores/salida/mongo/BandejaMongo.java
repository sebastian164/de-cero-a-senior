package tienda.adaptadores.salida.mongo;

import static com.mongodb.client.model.Filters.eq;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import nosql.aplicacion.BandejaDeEventos;
import org.bson.Document;
import tienda.aplicacion.EventoDePedido;
import tienda.aplicacion.puertos.salida.PublicadorDeEventos;

/** La bandeja de salida: el evento se escribe en la misma transacción que el cambio; otro proceso lo entrega después. */
public final class BandejaMongo implements PublicadorDeEventos, BandejaDeEventos {

    private final MongoCollection<Document> bandeja;
    private final Op op;

    public BandejaMongo(MongoDatabase db, UnidadDeTrabajoMongo unidad) {
        this.bandeja = db.getCollection("bandeja");
        this.op = new Op(unidad);
    }

    @Override
    public void publicar(EventoDePedido evento) {
        Errores.proteger(() -> op.insertar(bandeja, new Document("_id", evento.id()).append("tipo", evento.tipo()).append("pedidoId", evento.pedidoId())
                .append("cliente", evento.cliente()).append("ocurridoEn", Date.from(evento.ocurridoEn())).append("publicado", false)));
    }

    @Override
    public List<EventoDePedido> pendientes(int max) {
        return Errores.proteger(() -> {
            List<EventoDePedido> resultado = new ArrayList<>();
            op.buscar(bandeja, eq("publicado", false)).sort(Sorts.ascending("ocurridoEn")).limit(max).forEach(d -> resultado.add(aEvento(d)));
            return resultado;
        });
    }

    @Override
    public long contarPendientes() {
        return Errores.proteger(() -> bandeja.countDocuments(eq("publicado", false)));
    }

    @Override
    public void marcarPublicado(String id) {
        Errores.proteger(() -> bandeja.updateOne(eq("_id", id), Updates.set("publicado", true)));
    }

    private static EventoDePedido aEvento(Document d) {
        return new EventoDePedido(d.getString("_id"), d.getString("tipo"), d.getString("pedidoId"), d.getString("cliente"), d.getDate("ocurridoEn").toInstant());
    }
}
