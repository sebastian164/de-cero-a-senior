package banco.aplicacion.reactiva.puertos.entrada;

import banco.aplicacion.VistaDeCuenta;
import reactor.core.publisher.Mono;

public interface AbrirCuenta {

    Mono<VistaDeCuenta> abrir(String titular, long saldoInicialCentavos);
}
