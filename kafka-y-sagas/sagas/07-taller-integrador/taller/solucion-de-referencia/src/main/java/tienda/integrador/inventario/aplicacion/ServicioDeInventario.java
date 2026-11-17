package tienda.integrador.inventario.aplicacion;

import tienda.integrador.inventario.aplicacion.Puertos.AtenderOrden;
import tienda.integrador.inventario.aplicacion.Puertos.BandejaDeSalida;
import tienda.integrador.inventario.aplicacion.Puertos.BandejaDeSalida.Respuesta;
import tienda.integrador.inventario.aplicacion.Puertos.RegistroDeRespuestas;
import tienda.integrador.inventario.aplicacion.Puertos.UnidadDeTrabajo;
import tienda.integrador.inventario.aplicacion.Puertos.Existencias;
import tienda.integrador.inventario.dominio.Reserva;

/**
 * El patrón común de un participante: en UNA unidad de trabajo, (1) se comprueba si la orden ya se vio, (2) se aplica el efecto con su
 * propia idempotencia, (3) se guarda la respuesta y se deja en la tabla de salida. Una orden repetida repite la respuesta guardada.
 */
public final class ServicioDeInventario implements AtenderOrden {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ServicioDeInventario.class);
    private final RegistroDeRespuestas respuestas;
    private final BandejaDeSalida bandeja;
    private final UnidadDeTrabajo unidad;
    private final Existencias existencias;
    /** Fallo programado para las pruebas: la compensación falla siempre. */
    private final boolean fallaLaCompensacion;

    public ServicioDeInventario(RegistroDeRespuestas respuestas, BandejaDeSalida bandeja, UnidadDeTrabajo unidad, Existencias existencias, boolean fallaLaCompensacion) {
        this.respuestas = respuestas;
        this.bandeja = bandeja;
        this.unidad = unidad;
        this.existencias = existencias;
        this.fallaLaCompensacion = fallaLaCompensacion;
    }

    @Override
    public void atender(Orden orden) {
        unidad.ejecutar(() -> {
            String tipo = respuestas.buscar(orden.id()).orElseGet(() -> {
                String nuevo = ejecutar(orden);
                respuestas.guardar(orden.id(), nuevo);
                return nuevo;
            });
            bandeja.responder(new Respuesta(orden.id() + "->" + tipo, orden.sagaId(), orden.correlacion(), tipo));
            LOG.info("correlacion={} saga={} {} -> {}", orden.correlacion(), orden.sagaId(), orden.tipo(), tipo);
            return null;
        });
    }

    private String ejecutar(Orden o) {
        return switch (o.tipo()) {
            case "reservar-inventario" -> reservar(o);
            case "liberar-inventario" -> liberar(o);
            default -> throw new IllegalArgumentException("orden desconocida: " + o.tipo());
        };
    }

    private String reservar(Orden o) {
        if (existencias.hayLapida(o.sagaId())) return "sin-existencias";                  // ya se liberó: la reserva llega tarde
        if (!Reserva.cantidadValida(o.cantidad()) || !existencias.descontar(o.producto(), o.cantidad())) return "sin-existencias";
        existencias.registrarReserva(o.sagaId(), o.producto(), o.cantidad());
        return "inventario-reservado";
    }

    private String liberar(Orden o) {
        if (fallaLaCompensacion) throw new IllegalStateException("la compensación de inventario está rota (fallo programado)");
        if (!existencias.liberarReserva(o.sagaId())) existencias.ponerLapida(o.sagaId());   // nada que liberar todavía: lápida
        return "inventario-liberado";
    }
}
