package banco.aplicacion.reactiva.puertos.salida;

import reactor.core.publisher.Mono;

public interface OrdenesProcesadas {

    /** Emite falso si la clave ya estaba registrada. */
    Mono<Boolean> registrarSiEsNueva(String clave);
}
