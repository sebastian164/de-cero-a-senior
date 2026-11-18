package banco.aplicacion.reactiva.puertos.salida;

import banco.aplicacion.EventoDeCuenta;
import reactor.core.publisher.Mono;

public interface PublicadorDeEventos {

    Mono<Void> publicar(EventoDeCuenta evento);
}
