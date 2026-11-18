package banco.adaptadores.salida.r2dbc;

import banco.aplicacion.reactiva.puertos.salida.UnidadDeTrabajo;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

/**
 * El puerto de transacciones sobre R2DBC. La transacción no vive en el hilo (el trabajo pasa por varios):
 * viaja en el contexto de la suscripción, y DatabaseClient la encuentra ahí.
 */
@Component
public class UnidadDeTrabajoR2dbc implements UnidadDeTrabajo {

    private final TransactionalOperator transacciones;

    public UnidadDeTrabajoR2dbc(TransactionalOperator transacciones) {
        this.transacciones = transacciones;
    }

    @Override
    public <T> Mono<T> ejecutar(Supplier<Mono<T>> trabajo) {
        return transacciones.transactional(Mono.defer(trabajo));     // si ya hay una en el contexto, se une a ella
    }
}
