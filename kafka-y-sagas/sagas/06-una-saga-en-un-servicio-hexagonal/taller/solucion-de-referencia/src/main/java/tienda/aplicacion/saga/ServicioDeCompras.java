package tienda.aplicacion.saga;

import java.util.List;
import tienda.aplicacion.VistaDePedido;
import tienda.aplicacion.puertos.entrada.CancelarPedido;
import tienda.aplicacion.puertos.entrada.ConfirmarPedido;
import tienda.aplicacion.puertos.entrada.ConsultarPedido;
import tienda.aplicacion.puertos.entrada.CrearPedido;
import tienda.aplicacion.puertos.salida.UnidadDeTrabajo;
import tienda.aplicacion.saga.Puertos.ConfirmarCompra;
import tienda.aplicacion.saga.Puertos.Envios;
import tienda.aplicacion.saga.Puertos.Inventario;
import tienda.aplicacion.saga.Puertos.Pagos;
import tienda.aplicacion.saga.Puertos.ReanudarSagas;
import tienda.aplicacion.saga.Puertos.RepositorioDeSagas;
import tienda.aplicacion.saga.Puertos.SinRespuesta;
import tienda.dominio.saga.SagaDeCompra;
import tienda.dominio.saga.SagaDeCompra.Estado;

/**
 * El orquestador como servicio de aplicación, sobre el servicio de pedidos hexagonal. Cada paso es: llamada remota SIN transacción, y
 * después una transacción local corta que registra el resultado. Una llamada sin respuesta deja la saga donde está: no se compensa
 * sin saber. reanudar() la retoma; los participantes son idempotentes por id de saga.
 * El pedido se crea, se confirma o se cancela SIEMPRE por los puertos de entrada del servicio de pedidos (CrearPedido, ConfirmarPedido,
 * CancelarPedido): sus reglas, su idempotencia y su bandeja de eventos siguen siendo las de siempre.
 */
public final class ServicioDeCompras implements ConfirmarCompra, ReanudarSagas {

    private final CrearPedido crearPedido;
    private final ConfirmarPedido confirmarPedido;
    private final CancelarPedido cancelarPedido;
    private final ConsultarPedido consultarPedido;
    private final RepositorioDeSagas sagas;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final Inventario inventario;
    private final Pagos pagos;
    private final Envios envios;

    public ServicioDeCompras(CrearPedido crearPedido, ConfirmarPedido confirmarPedido, CancelarPedido cancelarPedido, ConsultarPedido consultarPedido,
                             RepositorioDeSagas sagas, UnidadDeTrabajo unidadDeTrabajo, Inventario inventario, Pagos pagos, Envios envios) {
        this.crearPedido = crearPedido;
        this.confirmarPedido = confirmarPedido;
        this.cancelarPedido = cancelarPedido;
        this.consultarPedido = consultarPedido;
        this.sagas = sagas;
        this.unidadDeTrabajo = unidadDeTrabajo;
        this.inventario = inventario;
        this.pagos = pagos;
        this.envios = envios;
    }

    @Override
    public Estado confirmar(Compra compra) {
        // ── Transacción 1: el pedido PENDIENTE y su saga en RESERVANDO, juntos o nada
        SagaDeCompra saga = unidadDeTrabajo.ejecutar(() -> {
            VistaDePedido pedido = crearPedido.crear(new CrearPedido.Solicitud(compra.clave(), compra.cliente(),
                    List.of(new CrearPedido.Linea(compra.producto(), compra.unidades())))).pedido();
            return sagas.buscar(pedido.id()).orElseGet(() -> {
                SagaDeCompra nueva = SagaDeCompra.iniciar(pedido.id(), compra.ciudad());
                sagas.guardar(nueva);
                return nueva;
            });
        });
        // ── fin de la transacción 1
        return avanzar(saga.id());
    }

    @Override
    public int reanudar() {
        int cerradas = 0;
        for (SagaDeCompra abierta : sagas.abiertas()) {
            if (!avanzar(abierta.id()).abierta()) {
                cerradas++;
            }
        }
        return cerradas;
    }

    /** Avanza paso a paso hasta cerrar la saga o hasta que un participante no responda. */
    private Estado avanzar(String sagaId) {
        while (true) {
            SagaDeCompra saga = sagas.buscar(sagaId).orElseThrow();
            if (!saga.estado().abierta()) {
                return saga.estado();
            }
            VistaDePedido pedido = consultarPedido.porId(sagaId).orElseThrow();
            try {
                darUnPaso(saga, pedido);
            } catch (SinRespuesta sinRespuesta) {
                return saga.estado();                    // no se sabe qué pasó: se queda donde está y se reintentará
            }
        }
    }

    private void darUnPaso(SagaDeCompra saga, VistaDePedido pedido) {
        String id = pedido.id();
        VistaDePedido.Linea linea = pedido.lineas().get(0);
        switch (saga.estado()) {
            case RESERVANDO -> {
                Inventario.Respuesta r = inventario.reservar(id, linea.producto(), linea.cantidad());     // remota, sin transacción
                registrar(id, s -> {                                                                      // ── transacción local
                    if (r == Inventario.Respuesta.RESERVADO) {
                        s.inventarioReservado();
                    } else {
                        s.sinExistencias();
                    }
                });                                                                                       // ── fin
            }
            case COBRANDO -> {
                Pagos.Respuesta r = pagos.cobrar(id, pedido.totalCentavos());                             // remota, sin transacción
                registrar(id, s -> {                                                                      // ── transacción local
                    if (r == Pagos.Respuesta.APROBADO) {
                        s.cobrado();
                    } else {
                        s.pagoRechazado();
                    }
                });                                                                                       // ── fin
            }
            case PROGRAMANDO_ENVIO -> {
                Envios.Respuesta r = envios.programar(id, saga.ciudad());                                 // remota, sin transacción
                registrar(id, s -> {                                                                      // ── transacción local
                    if (r == Envios.Respuesta.PROGRAMADO) {
                        s.envioProgramado();
                    } else {
                        s.envioImposible();
                    }
                });                                                                                       // ── fin
            }
            case REEMBOLSANDO -> {
                pagos.reembolsar(id);                                                                     // remota, sin transacción
                registrar(id, SagaDeCompra::reembolsado);                                                 // ── transacción local
            }
            case LIBERANDO -> {
                inventario.liberar(id);                                                                   // remota, sin transacción
                registrar(id, SagaDeCompra::liberado);                                                    // ── transacción local
            }
            default -> throw new IllegalStateException(saga.estado().name());
        }
    }

    /** Una transacción local: la saga y, si se cerró, el pedido (por sus puertos de entrada), se confirman juntos. */
    private void registrar(String id, java.util.function.Consumer<SagaDeCompra> transicion) {
        unidadDeTrabajo.ejecutar(() -> {
            SagaDeCompra saga = sagas.buscar(id).orElseThrow();
            transicion.accept(saga);
            sagas.guardar(saga);
            if (!saga.estado().abierta()) {
                if (saga.estado() == Estado.COMPLETADA) {
                    confirmarPedido.confirmar(id);
                } else {
                    cancelarPedido.cancelar(id);
                }
            }
            return null;
        });
    }
}
