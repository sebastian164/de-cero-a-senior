package pedidos;
import reactor.core.publisher.Mono;
public interface Pedidos {
    Mono<Pedido> buscar(String id);      // vacío si no existe
    Mono<Pedido> guardar(Pedido pedido);
}
