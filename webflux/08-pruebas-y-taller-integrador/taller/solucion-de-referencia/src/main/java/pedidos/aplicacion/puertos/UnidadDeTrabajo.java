package pedidos.aplicacion.puertos;

import java.util.function.Supplier;
import reactor.core.publisher.Mono;

public interface UnidadDeTrabajo { <T> Mono<T> ejecutar(Supplier<Mono<T>> trabajo); }
