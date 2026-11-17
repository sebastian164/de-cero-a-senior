package nosql.aplicacion;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import tienda.aplicacion.ConflictoDeConcurrencia;
import tienda.aplicacion.EventoDePedido;
import tienda.aplicacion.puertos.salida.Inventario;
import tienda.aplicacion.puertos.salida.PublicadorDeEventos;
import tienda.aplicacion.puertos.salida.Reloj;
import tienda.aplicacion.puertos.salida.RepositorioDePedidos;
import tienda.aplicacion.puertos.salida.UnidadDeTrabajo;
import tienda.dominio.EstadoDePedido;
import tienda.dominio.Existencias;
import tienda.dominio.Pedido;
import tienda.dominio.Reserva;

/**
 * La tarea que cancela los pedidos PENDIENTE abandonados. Cada cancelación es una unidad de trabajo: comprueba el turno, cancela con la
 * regla del dominio ({@link Pedido#cancelar()}), devuelve las existencias ({@link Reserva#liberar}) y deja el evento en la bandeja,
 * igual que {@code ServicioDePedidos.cancelar}. Solo conoce puertos.
 */
public final class CancelarAbandonados {

    public record Resultado(int canceladas, int yaNoPendientes, int enConflicto) {
    }

    private final PedidosAbandonados abandonados;
    private final RepositorioDePedidos pedidos;
    private final Inventario inventario;
    private final RegistroDeTurnos turnos;
    private final UnidadDeTrabajo unidad;
    private final PublicadorDeEventos eventos;
    private final Reloj reloj;

    public CancelarAbandonados(PedidosAbandonados abandonados, RepositorioDePedidos pedidos, Inventario inventario, RegistroDeTurnos turnos,
                               UnidadDeTrabajo unidad, PublicadorDeEventos eventos, Reloj reloj) {
        this.abandonados = abandonados;
        this.pedidos = pedidos;
        this.inventario = inventario;
        this.turnos = turnos;
        this.unidad = unidad;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    /** @throws TurnoCaducado en cuanto un turno más nuevo haya escrito: quien llama debe soltar la tarea */
    public Resultado cancelar(String tarea, long turno, Instant creadosAntesDe, int max) {
        int canceladas = 0, yaNo = 0, conflictos = 0;
        for (String id : abandonados.idsPendientesAnterioresA(creadosAntesDe, max)) {
            try {
                if (unidad.ejecutar(() -> cancelarUno(tarea, turno, id))) {
                    canceladas++;
                } else {
                    yaNo++;
                }
            } catch (ConflictoDeConcurrencia alguienLoCambio) {
                conflictos++;                                    // otro lo cambió a la vez: la próxima pasada lo ve como es
            }
        }
        return new Resultado(canceladas, yaNo, conflictos);
    }

    private boolean cancelarUno(String tarea, long turno, String id) {
        turnos.exigir(tarea, turno);                             // la primera escritura de la transacción: si el turno está caducado, no se hace nada
        Pedido pedido = pedidos.buscar(id).orElse(null);
        if (pedido == null || pedido.estado() != EstadoDePedido.PENDIENTE) {
            return false;
        }
        pedido.cancelar();
        Map<String, Existencias> existencias = new LinkedHashMap<>();
        for (String producto : pedido.unidadesPorProducto().keySet()) {
            existencias.put(producto, inventario.existenciasDe(producto).orElseGet(() -> Existencias.de(producto, 0)));
        }
        Reserva.liberar(pedido, existencias);
        existencias.values().forEach(inventario::guardar);
        pedidos.guardar(pedido);
        eventos.publicar(new EventoDePedido("pedido-cancelado:" + id, "PedidoCancelado", id, pedido.cliente(), reloj.ahora()));
        return true;
    }
}
