package banco.avisos;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import org.bson.BsonValue;
import org.springframework.data.mongodb.core.ChangeStreamEvent;
import org.springframework.data.mongodb.core.ChangeStreamOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * MongoDB avisa cuando hay algo nuevo: un flujo de cambios es un cursor abierto sobre el registro de operaciones.
 * Cada evento trae una ficha de reanudación: guardándola, se puede seguir donde se quedó tras una caída.
 */
@Component
public class NotificadorPorFlujoDeCambios {

    private final ReactiveMongoTemplate mongo;
    private volatile BsonValue ultimaFicha;

    public NotificadorPorFlujoDeCambios(ReactiveMongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** Si hay una ficha guardada, reanuda justo después de ella; si no, empieza en este momento. */
    public Flux<Movimiento> avisos() {
        ChangeStreamOptions.ChangeStreamOptionsBuilder opciones = ChangeStreamOptions.builder()
                .filter(newAggregation(match(where("operationType").is("insert"))));    // solo altas
        if (ultimaFicha != null) {
            opciones.resumeAfter(ultimaFicha);
        }
        return mongo.changeStream("movimientos", opciones.build(), Movimiento.class)
                .doOnNext(evento -> ultimaFicha = evento.getResumeToken())   // en producción, se guarda en la base
                .mapNotNull(ChangeStreamEvent::getBody);
    }
}
