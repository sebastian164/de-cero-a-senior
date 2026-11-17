package tienda.integrador.pedidos.aplicacion;

import java.util.Optional;
import tienda.integrador.pedidos.aplicacion.Puertos.AceptarPedido;
import tienda.integrador.pedidos.aplicacion.Puertos.BandejaDeSalida;
import tienda.integrador.pedidos.aplicacion.Puertos.ConsultarPedido;
import tienda.integrador.pedidos.aplicacion.Puertos.MensajesProcesados;
import tienda.integrador.pedidos.aplicacion.Puertos.RegistrarResultadoDeSaga;
import tienda.integrador.pedidos.aplicacion.Puertos.RepositorioDePedidos;
import tienda.integrador.pedidos.aplicacion.Puertos.UnidadDeTrabajo;
import tienda.integrador.pedidos.dominio.Pedido;

/** Los casos de uso de Pedidos. Coordina puertos; la regla de qué es un pedido está en el dominio. */
public final class ServicioDePedidos implements AceptarPedido, ConsultarPedido, RegistrarResultadoDeSaga {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ServicioDePedidos.class);
    private final RepositorioDePedidos pedidos;
    private final MensajesProcesados procesados;
    private final BandejaDeSalida bandeja;
    private final UnidadDeTrabajo unidad;

    public ServicioDePedidos(RepositorioDePedidos pedidos, MensajesProcesados procesados, BandejaDeSalida bandeja, UnidadDeTrabajo unidad) {
        this.pedidos = pedidos;
        this.procesados = procesados;
        this.bandeja = bandeja;
        this.unidad = unidad;
    }

    @Override
    public Pedido aceptar(Solicitud s) {
        Pedido nuevo = Pedido.nuevo(s.id(), s.correlacion(), s.producto(), s.cantidad(), s.centavos(), s.ciudad());
        // ── una transacción: el pedido y su evento, juntos o nada (la API responde aunque Kafka esté caído)
        unidad.ejecutar(() -> {
            if (pedidos.crear(nuevo)) bandeja.anunciarPedidoCreado(nuevo);
            return null;
        });
        LOG.info("correlacion={} pedido={} aceptado por HTTP, PENDIENTE", s.correlacion(), s.id());
        return pedidos.buscar(s.id()).orElse(nuevo);
    }

    @Override
    public Optional<Pedido> porId(String id) {
        return pedidos.buscar(id);
    }

    @Override
    public long pendientes() {
        return pedidos.contarPendientes();
    }

    @Override
    public void registrar(Resultado r) {
        unidad.ejecutar(() -> {
            if (!procesados.marcarNuevo(r.eventoId())) return null;                                 // repetido
            pedidos.cerrar(r.pedidoId(), r.completada() ? Pedido.Estado.CONFIRMADO : Pedido.Estado.CANCELADO, r.completada() ? null : r.motivo());
            LOG.info("correlacion={} pedido={} -> {}", r.correlacion(), r.pedidoId(), r.completada() ? "CONFIRMADO" : "CANCELADO");
            return null;
        });
    }
}
