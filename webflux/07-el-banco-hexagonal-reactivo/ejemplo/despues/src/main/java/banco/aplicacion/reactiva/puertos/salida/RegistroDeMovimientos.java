package banco.aplicacion.reactiva.puertos.salida;

import banco.dominio.Movimiento;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface RegistroDeMovimientos {

    Mono<Void> registrar(Movimiento movimiento);

    Flux<Movimiento> de(String cuentaId);
}
