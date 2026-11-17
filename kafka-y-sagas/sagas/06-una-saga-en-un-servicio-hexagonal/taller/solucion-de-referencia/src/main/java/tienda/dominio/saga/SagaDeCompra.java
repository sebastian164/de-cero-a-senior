package tienda.dominio.saga;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** El estado de la saga de compra, con transiciones protegidas: no se puede saltar ni retroceder. */
public final class SagaDeCompra {

    public enum Estado {
        RESERVANDO,            // se pidió la reserva; aún no se sabe el resultado
        COBRANDO,              // reservado; se pidió el cobro
        PROGRAMANDO_ENVIO,     // cobrado; se pidió el despacho (el pivote)
        REEMBOLSANDO,          // compensación: devolver el cobro
        LIBERANDO,             // compensación: liberar la reserva
        COMPLETADA,
        CANCELADA;

        public boolean abierta() {
            return this != COMPLETADA && this != CANCELADA;
        }
    }

    public static final class TransicionInvalida extends RuntimeException {
        private static final long serialVersionUID = 1L;

        TransicionInvalida(String detalle) {
            super(detalle);
        }
    }

    private static final Map<Estado, Set<Estado>> PERMITIDAS = Map.of(
            Estado.RESERVANDO, EnumSet.of(Estado.COBRANDO, Estado.CANCELADA),
            Estado.COBRANDO, EnumSet.of(Estado.PROGRAMANDO_ENVIO, Estado.LIBERANDO),
            Estado.PROGRAMANDO_ENVIO, EnumSet.of(Estado.COMPLETADA, Estado.REEMBOLSANDO),
            Estado.REEMBOLSANDO, EnumSet.of(Estado.LIBERANDO),
            Estado.LIBERANDO, EnumSet.of(Estado.CANCELADA),
            Estado.COMPLETADA, EnumSet.noneOf(Estado.class),
            Estado.CANCELADA, EnumSet.noneOf(Estado.class));

    private final String id;           // el id de la saga es el del pedido: un pedido, una saga
    private final String ciudad;       // dato de la compra que el pedido hexagonal no guarda: adónde se envía
    private Estado estado;
    private String motivo;

    public SagaDeCompra(String id, Estado estado, String motivo, String ciudad) {
        this.id = id;
        this.estado = estado;
        this.motivo = motivo;
        this.ciudad = ciudad;
    }

    public SagaDeCompra(String id, Estado estado, String motivo) {
        this(id, estado, motivo, null);
    }

    public static SagaDeCompra iniciar(String pedidoId, String ciudad) {
        return new SagaDeCompra(pedidoId, Estado.RESERVANDO, null, ciudad);
    }

    public static SagaDeCompra iniciar(String pedidoId) {
        return iniciar(pedidoId, null);
    }

    // Cada operación nombra su estado de origen: no basta con que la arista exista, tiene que ser la del hecho que llega
    public void inventarioReservado() { pasar(Estado.RESERVANDO, Estado.COBRANDO); }
    public void sinExistencias() { pasar(Estado.RESERVANDO, Estado.CANCELADA); motivo = "sin existencias"; }
    public void cobrado() { pasar(Estado.COBRANDO, Estado.PROGRAMANDO_ENVIO); }
    public void pagoRechazado() { pasar(Estado.COBRANDO, Estado.LIBERANDO); motivo = "pago rechazado"; }
    public void envioProgramado() { pasar(Estado.PROGRAMANDO_ENVIO, Estado.COMPLETADA); }
    public void envioImposible() { pasar(Estado.PROGRAMANDO_ENVIO, Estado.REEMBOLSANDO); motivo = "envío imposible"; }
    public void reembolsado() { pasar(Estado.REEMBOLSANDO, Estado.LIBERANDO); }
    public void liberado() { pasar(Estado.LIBERANDO, Estado.CANCELADA); }

    private void pasar(Estado desde, Estado nuevo) {
        if (estado != desde || !PERMITIDAS.get(estado).contains(nuevo)) {
            throw new TransicionInvalida("saga " + id + ": " + estado + " → " + nuevo + " no está permitido");
        }
        estado = nuevo;
    }

    public SagaDeCompra copia() {
        return new SagaDeCompra(id, estado, motivo, ciudad);
    }

    public String id() { return id; }
    public Estado estado() { return estado; }
    public String motivo() { return motivo; }
    public String ciudad() { return ciudad; }
}
