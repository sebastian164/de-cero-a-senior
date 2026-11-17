package inventario.aplicacion;

import inventario.aplicacion.Puertos.EventosProcesados;
import inventario.aplicacion.Puertos.Existencias;
import inventario.aplicacion.Puertos.PublicadorDeResultados;
import inventario.aplicacion.Puertos.Transaccion;
import inventario.dominio.PedidoConfirmado;
import inventario.dominio.ResultadoDeReserva;

/**
 * El caso de uso: idempotente y transaccional, sin una sola anotación de Spring ni de Kafka. Se prueba sin infraestructura.
 * El resultado se publica DESPUÉS de confirmar. Si la publicación falla, el mensaje se reintenta y, como el evento ya
 * está marcado, el reintento no vuelve a descontar: solo republica el resultado guardado.
 */
public final class ReservarInventario {

    private final EventosProcesados procesados;
    private final Existencias existencias;
    private final PublicadorDeResultados publicador;
    private final Transaccion transaccion;

    public ReservarInventario(EventosProcesados procesados, Existencias existencias, PublicadorDeResultados publicador, Transaccion transaccion) {
        this.procesados = procesados;
        this.existencias = existencias;
        this.publicador = publicador;
        this.transaccion = transaccion;
    }

    public void ejecutar(PedidoConfirmado evento) {
        ResultadoDeReserva resultado = transaccion.ejecutar(() -> {
            if (!procesados.registrarSiEsNuevo(evento.id())) {                    // misma transacción que la reserva
                return procesados.resultadoDe(evento.id()).orElseThrow();
            }
            ResultadoDeReserva r = existencias.reservar(evento.pedidoId(), evento.lineas())
                    ? ResultadoDeReserva.RESERVADO : ResultadoDeReserva.INSUFICIENTE;
            procesados.guardarResultado(evento.id(), r);
            return r;
        });
        publicador.publicar(evento.id(), evento.pedidoId(), resultado);            // fuera de la transacción
    }
}
