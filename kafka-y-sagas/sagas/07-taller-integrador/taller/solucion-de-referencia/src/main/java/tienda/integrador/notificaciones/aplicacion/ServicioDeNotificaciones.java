package tienda.integrador.notificaciones.aplicacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tienda.integrador.notificaciones.aplicacion.Puertos.ReaccionarAlResultado;
import tienda.integrador.notificaciones.aplicacion.Puertos.RegistroDeNotificaciones;

/** Avisa al cliente del resultado de su compra. Idempotente por id de evento. */
public final class ServicioDeNotificaciones implements ReaccionarAlResultado {

    private static final Logger LOG = LoggerFactory.getLogger(ServicioDeNotificaciones.class);
    private final RegistroDeNotificaciones registro;

    public ServicioDeNotificaciones(RegistroDeNotificaciones registro) {
        this.registro = registro;
    }

    @Override
    public void reaccionar(Resultado r) {
        String mensaje = r.completada() ? "Tu pedido fue confirmado" : "Tu pedido fue cancelado: " + r.motivo();
        if (registro.registrar(r.eventoId(), r.pedidoId(), mensaje)) LOG.info("correlacion={} pedido={} notificación enviada", r.correlacion(), r.pedidoId());
    }
}
