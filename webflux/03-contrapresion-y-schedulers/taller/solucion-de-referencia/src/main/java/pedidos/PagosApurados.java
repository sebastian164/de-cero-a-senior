package pedidos;

import reactor.core.publisher.Mono;

/** El error de quien tiene prisa: Mono.just evalúa cobrar() ya, en el hilo que arma la cadena (el bucle). */
public final class PagosApurados implements Pagos {
    private final ClienteDePagosBloqueante cliente = new ClienteDePagosBloqueante();
    public Mono<Cobro> cobrar(Pedido p) { return Mono.just(cliente.cobrar(p)); }
}
