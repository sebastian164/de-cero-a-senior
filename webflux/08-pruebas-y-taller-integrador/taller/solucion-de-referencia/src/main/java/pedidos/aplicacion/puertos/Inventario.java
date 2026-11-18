package pedidos.aplicacion.puertos;

import reactor.core.publisher.Mono;
import tienda.dominio.Pedido;

/** El servicio externo de inventario. Reservar y liberar son idempotentes: la clave es el id del pedido. */
public interface Inventario {
    Mono<Void> reservar(Pedido pedido);
    Mono<Void> liberar(Pedido pedido);
}
