package pedidos;
import reactor.core.publisher.Mono;
public interface Pagos { Mono<Void> cobrar(Pedido pedido); }
