package avisos;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.core.publisher.Flux;

/** El rival: preguntar cada 500 ms qué pedidos cambiaron. */
public class NotificadorPorSondeo {
    private final ReactiveMongoTemplate mongo;
    public NotificadorPorSondeo(ReactiveMongoTemplate mongo) { this.mongo = mongo; }

    public Flux<Aviso> flujo(Duration cada) {
        AtomicReference<Date> ultimo = new AtomicReference<>(new Date());
        return Flux.interval(cada).onBackpressureDrop()      // si una consulta tarda más que el intervalo (la primera, en frío), se salta ese tick en vez de fallar con OverflowException
                .concatMap(t -> mongo.find(query(where("cambiado").gt(ultimo.get())).with(Sort.by("cambiado")), Document.class, "pedidos")
                .concatMap(d -> {
                    ultimo.set(d.getDate("cambiado"));
                    return mongo.insert(new Document("pedidoId", d.getString("_id")).append("estado", d.getString("estado"))
                            .append("cambiado", d.getDate("cambiado")).append("recibido", new Date()), "avisos_sondeo")
                            .thenReturn(new Aviso(d.getString("_id"), d.getString("estado"), d.getDate("cambiado").toInstant(), Instant.now()));
                }));
    }
}
