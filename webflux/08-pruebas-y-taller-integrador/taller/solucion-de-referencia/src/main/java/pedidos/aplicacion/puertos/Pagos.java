package pedidos.aplicacion.puertos;

import reactor.core.publisher.Mono;
import tienda.dominio.Pedido;

/** El servicio externo de pagos. La clave de idempotencia es el id del pedido: repetir `cobrar` no cobra dos veces. */
public interface Pagos {
    Mono<Void> cobrar(Pedido pedido);
    Mono<Void> reembolsar(Pedido pedido);
}
