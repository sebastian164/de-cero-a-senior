package avisos;

import java.time.Duration;
import java.time.Instant;
import org.bson.Document;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Cambia de estado pedidos (PENDIENTE a CONFIRMADO o RECHAZADO) a un ritmo continuo. */
public class Confirmador {
    private final ReactiveMongoTemplate mongo;
    public Confirmador(ReactiveMongoTemplate mongo) { this.mongo = mongo; }

    /** Crea los pedidos (inserciones: no producen avisos) y luego cambia el estado de cada uno. Devuelve cuántos cambios hizo. */
    public Mono<Long> correr(String prefijo, int cambios, long intervaloMs) {
        return Flux.range(1, cambios)
                .concatMap(i -> mongo.insert(new Document("_id", prefijo + i).append("estado", "PENDIENTE"), "pedidos"))
                .then(Mono.defer(() -> Flux.range(1, cambios)
                        .delayElements(Duration.ofMillis(intervaloMs))
                        .concatMap(i -> mongo.updateFirst(Query.query(Criteria.where("_id").is(prefijo + i)),
                                new Update().set("estado", i % 5 == 0 ? "RECHAZADO" : "CONFIRMADO").set("cambiado", Instant.now()), "pedidos"))
                        .count()));
    }

    public Mono<Long> cambiarUno(String id, String estado) {
        return mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),
                new Update().set("estado", estado).set("cambiado", Instant.now()), "pedidos").thenReturn(1L);
    }
}
