package pedidos.aplicacion.puertos;

import reactor.core.publisher.Mono;
import tienda.dominio.EstadoDePedido;
import tienda.dominio.Pedido;

public interface RepositorioDePedidos {
    Mono<Pedido> buscar(String id);               // vacío si no existe
    /** Guarda el estado solo si en la base sigue como se leyó; si otro lo cambió, termina con ConflictoDeConcurrencia. */
    Mono<Void> guardar(Pedido pedido, EstadoDePedido estadoLeido);
}
