package pedidos.aplicacion;

import java.time.Duration;
import java.util.Map;
import java.util.TreeSet;
import pedidos.aplicacion.Errores.*;
import pedidos.aplicacion.puertos.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import tienda.aplicacion.ConflictoDeConcurrencia;
import tienda.aplicacion.PedidoNoEncontrado;
import tienda.dominio.EstadoDePedido;
import tienda.dominio.Existencias;
import tienda.dominio.Pedido;
import tienda.dominio.Reserva;

/**
 * Confirmar un pedido toca dos servicios externos (inventario y pagos) y la base propia. Una transacción de base de datos
 * no puede abarcar un cobro: el orden es reservar, cobrar (con clave de idempotencia) y SOLO ENTONCES una transacción corta
 * con el estado y las existencias. Si un paso posterior falla, se compensa con la operación contraria (reembolsar, liberar).
 */
public class ServicioDePedidos {
    public static final Duration LIMITE_DE_PAGOS = Duration.ofSeconds(3);
    public static final int INTENTOS = 3;             // tres conflictos seguidos y se rinde

    private final RepositorioDePedidos pedidos; private final Almacen almacen; private final UnidadDeTrabajo unidad;
    private final Inventario inventario; private final Pagos pagos; private final Eventos eventos;

    public ServicioDePedidos(RepositorioDePedidos pedidos, Almacen almacen, UnidadDeTrabajo unidad, Inventario inventario, Pagos pagos, Eventos eventos) {
        this.pedidos = pedidos; this.almacen = almacen; this.unidad = unidad; this.inventario = inventario; this.pagos = pagos; this.eventos = eventos;
    }

    public Mono<Pedido> consultar(String id) { return pedidos.buscar(id).switchIfEmpty(Mono.error(() -> new PedidoNoEncontrado(id))); }

    /** Un pedido terminado responde de inmediato su estado final; uno en curso empieza por el estado actual y sigue los eventos. */
    public Flux<String> eventos(String id) {
        return consultar(id).flatMapMany(p -> p.estado() == EstadoDePedido.CONFIRMADO || p.estado() == EstadoDePedido.CANCELADO
                ? Flux.just(p.estado().name())
                : eventos.flujo(id).startWith(p.estado().name()));
    }

    public Mono<Pedido> confirmar(String id) {
        return consultar(id).flatMap(pedido -> {
            pedido.confirmar();         // el dominio decide ANTES de tocar nada externo: si lanza, no hay nada que deshacer
            return reservar(pedido)
                    .then(Mono.defer(() -> cobrar(pedido)))
                    .then(Mono.defer(() -> transaccionCorta(id).onErrorResume(e -> compensarTrasElCobro(id, pedido, e))));
        }).doOnSuccess(p -> { if (p != null) eventos.emitir(id, "CONFIRMADO"); eventos.terminar(id); })
          .doOnError(e -> { eventos.emitir(id, "FALLIDO"); eventos.terminar(id); });
    }

    /** Si el inventario no puede, no hay nada que devolver; si falla de otra manera (tiempo, red), la reserva queda dudosa y se libera. */
    private Mono<Void> reservar(Pedido pedido) {
        return inventario.reservar(pedido)
                .onErrorResume(e -> e instanceof SinExistenciasEnInventario ? Mono.error(e) : liberar(pedido).then(Mono.error(e)))
                .then(Mono.fromRunnable(() -> eventos.emitir(pedido.id(), "RESERVADO")));
    }

    /** Si el cobro falla no se llega a la base: solo hay una reserva que devolver (y, si se acabó el tiempo, un cobro dudoso que reembolsar). */
    private Mono<Void> cobrar(Pedido pedido) {
        return pagos.cobrar(pedido)
                .timeout(LIMITE_DE_PAGOS, Mono.error(() -> new ServicioExternoNoDisponible("pagos", null)))
                .onErrorResume(e -> (e instanceof PagoRechazado ? Mono.<Void>empty() : reembolsar(pedido)).then(liberar(pedido)).then(Mono.error(e)))
                .then(Mono.fromRunnable(() -> eventos.emitir(pedido.id(), "COBRADO")));
    }

    /**
     * El cobro se hizo y la transacción falló (sin existencias propias, conflictos agotados...). Se deshace lo externo. Excepción: si otra
     * petición ya confirmó este pedido, el cobro y la reserva son SUYOS (misma clave de idempotencia) y no se tocan.
     */
    private Mono<Pedido> compensarTrasElCobro(String id, Pedido pedido, Throwable error) {
        return pedidos.buscar(id).map(p -> p.estado() == EstadoDePedido.CONFIRMADO).defaultIfEmpty(false)
                .flatMap(yaConfirmado -> yaConfirmado ? Mono.<Pedido>error(error)
                        : reembolsar(pedido).then(liberar(pedido)).then(Mono.<Pedido>error(error)));
    }

    private Mono<Void> reembolsar(Pedido pedido) { return pagos.reembolsar(pedido).onErrorResume(e -> Mono.empty()); }   // si también falla, queda para una bandeja de pendientes
    private Mono<Void> liberar(Pedido pedido) { return inventario.liberar(pedido).onErrorResume(e -> Mono.empty()); }

    /** Lo único que ocurre dentro de la transacción: leer, decidir en el dominio, descontar y guardar. Sin red. Un conflicto reintenta SOLO esto. */
    private Mono<Pedido> transaccionCorta(String id) {
        return Mono.defer(() -> unidad.ejecutar(() -> consultar(id).flatMap(pedido -> {
                    EstadoDePedido leido = pedido.estado();
                    pedido.confirmar();
                    return existenciasDe(pedido).flatMap(existencias -> {
                        Reserva.reservar(pedido, existencias);
                        return Flux.fromIterable(existencias.values()).concatMap(almacen::guardar)
                                .then(pedidos.guardar(pedido, leido)).thenReturn(pedido);
                    });
                })))
                .retryWhen(Retry.max(INTENTOS - 1).filter(e -> e instanceof ConflictoDeConcurrencia).onRetryExhaustedThrow((spec, senal) -> senal.failure()));
    }

    private Mono<Map<String, Existencias>> existenciasDe(Pedido pedido) {
        return Flux.fromIterable(new TreeSet<>(pedido.unidadesPorProducto().keySet()))
                .concatMap(p -> almacen.existenciasDe(p).switchIfEmpty(Mono.fromSupplier(() -> Existencias.de(p, 0))))
                .collectMap(Existencias::producto);
    }
}
