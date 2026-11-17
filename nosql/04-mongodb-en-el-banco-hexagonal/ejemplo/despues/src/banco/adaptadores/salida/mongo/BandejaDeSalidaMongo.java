package banco.adaptadores.salida.mongo;

import static com.mongodb.client.model.Filters.eq;

import banco.aplicacion.EventoDeCuenta;
import banco.aplicacion.puertos.salida.PublicadorDeEventos;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Sorts;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import org.bson.Document;

/**
 * Publicar un evento es guardarlo en la bandeja de salida, dentro de la misma transacción que el cambio.
 * Otro proceso lo entrega después. Es el outbox del módulo de Kafka, con MongoDB.
 */
public final class BandejaDeSalidaMongo implements PublicadorDeEventos {

    private final MongoCollection<Document> bandeja;
    private final UnidadDeTrabajoMongo transacciones;

    public BandejaDeSalidaMongo(MongoDatabase base, UnidadDeTrabajoMongo transacciones) {
        this.bandeja = base.getCollection(EsquemaMongo.BANDEJA);
        this.transacciones = transacciones;
    }

    @Override
    public void publicar(EventoDeCuenta evento) {
        Document documento = new Document("_id", evento.id())
                .append("tipo", evento.tipo())
                .append("cuentaId", evento.cuentaId())
                .append("centavos", evento.centavos())
                .append("ocurridoEn", Date.from(evento.ocurridoEn()));
        transacciones.sesionEnCurso().ifPresentOrElse(
                sesion -> bandeja.insertOne(sesion, documento),
                () -> bandeja.insertOne(documento));
    }

    /** Entrega lo confirmado, en orden. Si el destino falla, el evento se queda para el siguiente intento. */
    public int entregarPendientes(Consumer<EventoDeCuenta> destino) {
        List<Document> pendientes = bandeja.find().sort(Sorts.ascending("ocurridoEn", "_id")).into(new ArrayList<>());
        int entregados = 0;
        for (Document documento : pendientes) {
            destino.accept(new EventoDeCuenta(documento.getString("_id"), documento.getString("tipo"),
                    documento.getString("cuentaId"), documento.getLong("centavos"), documento.getDate("ocurridoEn").toInstant()));
            bandeja.deleteOne(eq("_id", documento.getString("_id")));     // si se cae aquí, se entregará otra vez: el id permite descartarlo
            entregados++;
        }
        return entregados;
    }
}
