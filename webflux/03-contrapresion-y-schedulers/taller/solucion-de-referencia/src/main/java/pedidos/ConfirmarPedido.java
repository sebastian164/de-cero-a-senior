package pedidos;

import java.time.Duration;
import reactor.core.publisher.Mono;

public final class ConfirmarPedido {
    static final Duration LIMITE_COBRO = Duration.ofSeconds(3);

    private final Pedidos pedidos; private final Inventario inventario; private final Antifraude antifraude; private final Pagos pagos;

    public ConfirmarPedido(Pedidos pedidos, Inventario inventario, Antifraude antifraude, Pagos pagos) {
        this.pedidos = pedidos; this.inventario = inventario; this.antifraude = antifraude; this.pagos = pagos;
    }

    public Mono<Pedido> confirmar(String pedidoId) {
        return pedidos.buscar(pedidoId)
                .switchIfEmpty(Mono.error(() -> new PedidoNoEncontrado(pedidoId)))
                // reserva y antifraude en paralelo; el cobro solo empieza cuando las dos terminaron bien
                .flatMap(p -> Mono.zip(inventario.reservar(p), antifraude.aprobar(p))
                        .flatMap(par -> par.getT2()
                                ? Mono.just(p)
                                : Mono.<Pedido>error(new RechazadoPorAntifraude(pedidoId))))
                .flatMap(p -> pagos.cobrar(p)
                        .timeout(LIMITE_COBRO, Mono.error(() -> new PagoDemasiadoLento(pedidoId)))
                        .thenReturn(p))
                .flatMap(p -> pedidos.guardar(p.confirmado()));
    }
}
