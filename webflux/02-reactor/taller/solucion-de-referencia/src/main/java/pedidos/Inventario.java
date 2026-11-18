package pedidos;
import reactor.core.publisher.Mono;
public interface Inventario { Mono<Reserva> reservar(Pedido pedido); }
