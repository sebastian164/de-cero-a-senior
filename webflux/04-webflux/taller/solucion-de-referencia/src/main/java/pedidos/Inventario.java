package pedidos;
import reactor.core.publisher.Mono;
public interface Inventario { Mono<Void> reservar(Pedido pedido); }
