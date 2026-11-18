package pedidos;
import reactor.core.publisher.Mono;
public interface Pagos { Mono<Cobro> cobrar(Pedido pedido); }
