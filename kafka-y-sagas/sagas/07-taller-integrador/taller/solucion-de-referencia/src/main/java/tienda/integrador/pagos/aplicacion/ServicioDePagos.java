package tienda.integrador.pagos.aplicacion;

import tienda.integrador.pagos.aplicacion.Puertos.AtenderOrden;
import tienda.integrador.pagos.aplicacion.Puertos.BandejaDeSalida;
import tienda.integrador.pagos.aplicacion.Puertos.BandejaDeSalida.Respuesta;
import tienda.integrador.pagos.aplicacion.Puertos.RegistroDeRespuestas;
import tienda.integrador.pagos.aplicacion.Puertos.UnidadDeTrabajo;
import tienda.integrador.pagos.aplicacion.Puertos.Cobros;
import tienda.integrador.pagos.dominio.PasarelaSimulada;

/**
 * El patrón común de un participante: en UNA unidad de trabajo, (1) se comprueba si la orden ya se vio, (2) se aplica el efecto con su
 * propia idempotencia, (3) se guarda la respuesta y se deja en la tabla de salida. Una orden repetida repite la respuesta guardada.
 */
public final class ServicioDePagos implements AtenderOrden {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ServicioDePagos.class);
    private final RegistroDeRespuestas respuestas;
    private final BandejaDeSalida bandeja;
    private final UnidadDeTrabajo unidad;
    private final Cobros cobros;

    public ServicioDePagos(RegistroDeRespuestas respuestas, BandejaDeSalida bandeja, UnidadDeTrabajo unidad, Cobros cobros) {
        this.respuestas = respuestas;
        this.bandeja = bandeja;
        this.unidad = unidad;
        this.cobros = cobros;
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
            case "cobrar" -> {
                if (cobros.hayLapida(o.sagaId())) yield "pago-rechazado";          // ya se reembolsó: el cobro llega tarde
                if (PasarelaSimulada.rechaza(o.centavos())) yield "pago-rechazado";  // la pasarela dice que no
                cobros.cobrar(o.sagaId(), o.centavos());
                yield "pago-aprobado";
            }
            case "reembolsar" -> {
                if (!cobros.reembolsar(o.sagaId())) cobros.ponerLapida(o.sagaId());   // nada que reembolsar todavía: lápida
                yield "pago-reembolsado";
            }
            default -> throw new IllegalArgumentException("orden desconocida: " + o.tipo());
        };
    }
}
