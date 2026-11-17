package tienda.integrador.envios.aplicacion;

import tienda.integrador.envios.aplicacion.Puertos.AtenderOrden;
import tienda.integrador.envios.aplicacion.Puertos.BandejaDeSalida;
import tienda.integrador.envios.aplicacion.Puertos.BandejaDeSalida.Respuesta;
import tienda.integrador.envios.aplicacion.Puertos.RegistroDeRespuestas;
import tienda.integrador.envios.aplicacion.Puertos.UnidadDeTrabajo;
import tienda.integrador.envios.aplicacion.Puertos.Despachos;
import tienda.integrador.envios.dominio.Cobertura;

/**
 * El patrón común de un participante: en UNA unidad de trabajo, (1) se comprueba si la orden ya se vio, (2) se aplica el efecto con su
 * propia idempotencia, (3) se guarda la respuesta y se deja en la tabla de salida. Una orden repetida repite la respuesta guardada.
 */
public final class ServicioDeEnvios implements AtenderOrden {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ServicioDeEnvios.class);
    private final RegistroDeRespuestas respuestas;
    private final BandejaDeSalida bandeja;
    private final UnidadDeTrabajo unidad;
    private final Despachos despachos;

    public ServicioDeEnvios(RegistroDeRespuestas respuestas, BandejaDeSalida bandeja, UnidadDeTrabajo unidad, Despachos despachos) {
        this.respuestas = respuestas;
        this.bandeja = bandeja;
        this.unidad = unidad;
        this.despachos = despachos;
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
        if (!o.tipo().equals("programar-envio")) throw new IllegalArgumentException("orden desconocida: " + o.tipo());
        if (!Cobertura.llega(o.ciudad())) return "envio-imposible";
        despachos.programar(o.sagaId(), o.ciudad());
        return "envio-programado";
    }
}
