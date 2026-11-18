package pedidos;
import reactor.core.publisher.Mono;
public interface Antifraude { Mono<Boolean> aprobar(Pedido pedido); }
