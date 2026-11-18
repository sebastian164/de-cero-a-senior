package pedidos;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ConfirmarPedido {
    private final AlmacenDePedidos almacen; private final Inventario inventario; private final Pagos pagos; private final EventosDePedido eventos;

    public ConfirmarPedido(AlmacenDePedidos almacen, Inventario inventario, Pagos pagos, EventosDePedido eventos) {
        this.almacen = almacen; this.inventario = inventario; this.pagos = pagos; this.eventos = eventos;
    }

    public Mono<Pedido> confirmar(String id) {
        return almacen.buscar(id)
                .switchIfEmpty(Mono.error(() -> new PedidoNoEncontrado(id)))
                .flatMap(p -> inventario.reservar(p)
                        .then(Mono.fromRunnable(() -> eventos.emitir(id, "RESERVADO")))
                        .then(Mono.defer(() -> pagos.cobrar(p)))
                        .then(Mono.fromRunnable(() -> eventos.emitir(id, "COBRADO")))
                        .then(Mono.defer(() -> almacen.guardar(p.con("CONFIRMADO"))))
                        .doOnSuccess(c -> { eventos.emitir(id, "CONFIRMADO"); eventos.terminar(id); })
                        .doOnError(e -> { eventos.emitir(id, "FALLIDO"); eventos.terminar(id); }));
    }
}
