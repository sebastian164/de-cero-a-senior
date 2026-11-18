package banco.aplicacion.reactiva.puertos.salida;

import java.util.function.Supplier;
import reactor.core.publisher.Mono;

/**
 * Todo lo que ocurra dentro se confirma junto, o no ocurre. Recibe un Supplier y no un Mono ya armado:
 * cada suscripción (cada reintento) debe construir el trabajo de nuevo, dentro de una transacción nueva.
 */
public interface UnidadDeTrabajo {

    <T> Mono<T> ejecutar(Supplier<Mono<T>> trabajo);
}
