package pedidos.aplicacion.reactiva.puertos;

import java.util.function.Supplier;
import reactor.core.publisher.Mono;

/** Recibe un Supplier: cada reintento arma el trabajo de nuevo y no reutiliza un Mono ya usado. */
public interface UnidadDeTrabajo { <T> Mono<T> ejecutar(Supplier<Mono<T>> trabajo); }
