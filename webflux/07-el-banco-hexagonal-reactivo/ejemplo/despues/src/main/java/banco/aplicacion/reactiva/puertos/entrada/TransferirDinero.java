package banco.aplicacion.reactiva.puertos.entrada;

import reactor.core.publisher.Mono;

public interface TransferirDinero {

    /** La clave identifica la orden: reenviar la misma orden no transfiere dos veces. */
    record Orden(String clave, String origenId, String destinoId, long centavos) {
    }

    enum Resultado {
        REALIZADA,
        REPETIDA
    }

    Mono<Resultado> transferir(Orden orden);
}
