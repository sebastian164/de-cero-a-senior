package banco.aplicacion.reactiva.puertos.entrada;

import banco.aplicacion.VistaDeCuenta;
import banco.dominio.Movimiento;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ConsultarCuenta {

    /** Vacío si no existe: Mono vacío ocupa el lugar de Optional.empty(). */
    Mono<VistaDeCuenta> porId(String cuentaId);

    Flux<Movimiento> movimientosDe(String cuentaId);
}
