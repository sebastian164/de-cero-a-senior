package inventario.aplicacion;

import inventario.aplicacion.Puertos.EventosVistos;
import inventario.aplicacion.Puertos.RepositorioDeExistencias;
import inventario.aplicacion.Puertos.UnidadDeTrabajo;
import inventario.dominio.Existencias;

/** El caso de uso. Idempotente en la aplicación: la marca de "evento visto" y la reserva van en la misma unidad de trabajo. */
public final class ServicioDeInventario implements ReservarPorPedido {

    private final RepositorioDeExistencias existencias;
    private final EventosVistos vistos;
    private final UnidadDeTrabajo unidad;

    public ServicioDeInventario(RepositorioDeExistencias existencias, EventosVistos vistos, UnidadDeTrabajo unidad) {
        this.existencias = existencias;
        this.vistos = vistos;
        this.unidad = unidad;
    }

    @Override
    public Resultado reservar(Orden orden) {
        return unidad.ejecutar(() -> {
            if (!vistos.registrarSiEsNuevo(orden.eventoId())) return Resultado.YA_PROCESADO;
            Existencias actual = existencias.buscar(orden.producto()).orElse(new Existencias(orden.producto(), 0));
            if (!actual.puedeReservar(orden.cantidad())) return Resultado.SIN_EXISTENCIAS;
            existencias.guardar(actual.reservar(orden.cantidad()));
            existencias.registrarReserva(orden.pedidoId(), orden.producto(), orden.cantidad());
            return Resultado.RESERVADO;
        });
    }
}
