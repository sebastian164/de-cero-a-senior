package pedidos.aplicacion.reactiva;

import java.util.Map;
import java.util.TreeSet;
import pedidos.aplicacion.reactiva.puertos.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import tienda.aplicacion.ConflictoDeConcurrencia;
import tienda.aplicacion.PedidoNoEncontrado;
import tienda.dominio.EstadoDePedido;
import tienda.dominio.Existencias;
import tienda.dominio.Pedido;
import tienda.dominio.Reserva;

public class ServicioDePedidos {
    public static final int INTENTOS = 3;
    private final RepositorioDePedidos pedidos; private final Inventario inventario; private final UnidadDeTrabajo unidad;

    public ServicioDePedidos(RepositorioDePedidos pedidos, Inventario inventario, UnidadDeTrabajo unidad) {
        this.pedidos = pedidos; this.inventario = inventario; this.unidad = unidad;
    }

    public Mono<Pedido> consultar(String id) { return pedidos.buscar(id).switchIfEmpty(Mono.error(() -> new PedidoNoEncontrado(id))); }

    public Mono<Pedido> confirmar(String id) {
        return Mono.defer(() -> unidad.ejecutar(() -> consultar(id).flatMap(pedido -> {
                    EstadoDePedido leido = pedido.estado();
                    pedido.confirmar();                                       // el mismo dominio, sin cambios
                    return existenciasDe(pedido).flatMap(existencias -> {
                        Reserva.reservar(pedido, existencias);                // si no alcanza, lanza y la transacción se revierte
                        return Flux.fromIterable(existencias.values()).concatMap(inventario::guardar)
                                .then(pedidos.guardar(pedido, leido)).thenReturn(pedido);
                    });
                })))
                .retryWhen(Retry.max(INTENTOS - 1).filter(e -> e instanceof ConflictoDeConcurrencia).onRetryExhaustedThrow((spec, senal) -> senal.failure()));
    }

    private Mono<Map<String, Existencias>> existenciasDe(Pedido pedido) {
        return Flux.fromIterable(new TreeSet<>(pedido.unidadesPorProducto().keySet()))
                .concatMap(p -> inventario.existenciasDe(p).switchIfEmpty(Mono.fromSupplier(() -> Existencias.de(p, 0))))
                .collectMap(Existencias::producto);
    }
}
