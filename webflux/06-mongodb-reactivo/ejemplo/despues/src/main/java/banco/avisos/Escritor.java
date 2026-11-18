package banco.avisos;

import java.time.Instant;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Otro proceso cualquiera que registra movimientos. El notificador no lo conoce: solo ve la colección. */
@Component
public class Escritor {

    private final ReactiveMongoTemplate mongo;

    public Escritor(ReactiveMongoTemplate mongo) {
        this.mongo = mongo;
    }

    public Mono<Movimiento> registrar(String cuentaId, long centavos) {
        return mongo.insert(new Movimiento(null, cuentaId, centavos, Instant.now()));
    }
}
