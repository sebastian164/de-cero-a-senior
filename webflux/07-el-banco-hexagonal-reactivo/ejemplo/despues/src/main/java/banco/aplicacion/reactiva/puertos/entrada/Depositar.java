package banco.aplicacion.reactiva.puertos.entrada;

import banco.aplicacion.VistaDeCuenta;
import reactor.core.publisher.Mono;

public interface Depositar {

    Mono<VistaDeCuenta> depositar(String cuentaId, long centavos);
}
