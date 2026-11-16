package tienda.pedidos.dominio;

import java.time.Duration;
import java.time.LocalDateTime;

/** Modelo: las dos reglas de cancelación viven aquí, sin HTTP ni Spring. */
public class Pedido {

    static final Duration PLAZO_CANCELACION = Duration.ofHours(24);

    private final long id;
    private final String cliente;
    private final LocalDateTime fecha;
    private EstadoPedido estado;

    public Pedido(long id, String cliente, LocalDateTime fecha, EstadoPedido estado) {
        this.id = id;
        this.cliente = cliente;
        this.fecha = fecha;
        this.estado = estado;
    }

    public void cancelar(LocalDateTime ahora) {
        if (estado == EstadoPedido.ENVIADO) throw new CancelacionRechazada("Ya fue enviado");
        if (Duration.between(fecha, ahora).compareTo(PLAZO_CANCELACION) > 0) {
            throw new CancelacionRechazada("Pasaron más de 24 horas");
        }
        estado = EstadoPedido.CANCELADO;
    }

    public long id() { return id; }
    public String cliente() { return cliente; }
    public EstadoPedido estado() { return estado; }
}
