package tienda.integrador.fidelidad.aplicacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tienda.integrador.fidelidad.aplicacion.Puertos.Puntos;
import tienda.integrador.fidelidad.aplicacion.Puertos.ReaccionarAlResultado;
import tienda.integrador.fidelidad.dominio.Recompensa;

/** Suma puntos solo a los pedidos confirmados. Idempotente por pedido. */
public final class ServicioDeFidelidad implements ReaccionarAlResultado {

    private static final Logger LOG = LoggerFactory.getLogger(ServicioDeFidelidad.class);
    private final Puntos puntos;

    public ServicioDeFidelidad(Puntos puntos) {
        this.puntos = puntos;
    }

    @Override
    public void reaccionar(Resultado r) {
        if (!r.completada()) return;                                                   // los puntos se suman solo a pedidos confirmados
        if (puntos.sumar(r.pedidoId(), Recompensa.PUNTOS_POR_COMPRA)) LOG.info("correlacion={} pedido={} +{} puntos", r.correlacion(), r.pedidoId(), Recompensa.PUNTOS_POR_COMPRA);
    }
}
