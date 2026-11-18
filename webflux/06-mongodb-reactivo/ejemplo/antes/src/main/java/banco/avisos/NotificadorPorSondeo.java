package banco.avisos;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/** "¿Hay algo nuevo?" cada segundo. Reactivo por fuera, pero sigue siendo preguntar una y otra vez. */
@Component
public class NotificadorPorSondeo {

    static final Duration CADA = Duration.ofSeconds(1);

    private final ReactiveMongoTemplate mongo;

    public NotificadorPorSondeo(ReactiveMongoTemplate mongo) {
        this.mongo = mongo;
    }

    public Flux<Movimiento> avisos() {
        AtomicReference<Instant> ultimoVisto = new AtomicReference<>(Instant.now());
        return Flux.interval(CADA)
                .concatMap(tick -> mongo.find(query(where("fecha").gt(ultimoVisto.get())).with(Sort.by("fecha")), Movimiento.class)
                        .doOnNext(movimiento -> ultimoVisto.set(movimiento.fecha())));
    }
}
