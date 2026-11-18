package banco.aplicacion.reactiva.puertos.salida;

import banco.dominio.Cuenta;
import reactor.core.publisher.Mono;

public interface RepositorioDeCuentas {

    /** Sin E/S: sigue siendo síncrono. Un Mono aquí solo añadiría ruido. */
    String siguienteId();

    /** Termina con error ConflictoDeConcurrencia si otro cambió la cuenta desde que se leyó. */
    Mono<Void> guardar(Cuenta cuenta);

    Mono<Cuenta> buscar(String id);
}
