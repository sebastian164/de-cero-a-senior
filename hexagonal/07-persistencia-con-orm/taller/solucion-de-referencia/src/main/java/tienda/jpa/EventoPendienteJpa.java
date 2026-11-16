package tienda.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import tienda.aplicacion.EventoDePedido;

/** Una fila de la bandeja de salida. */
@Entity
@Table(name = "evento_pendiente")
public class EventoPendienteJpa {

    @Id
    private String id;

    @Column(nullable = false)
    private String tipo;

    @Column(name = "pedido_id", nullable = false)
    private String pedidoId;

    @Column(nullable = false)
    private String cliente;

    @Column(name = "ocurrido_en", nullable = false)
    private Instant ocurridoEn;

    protected EventoPendienteJpa() {
    }

    static EventoPendienteJpa desde(EventoDePedido evento) {
        EventoPendienteJpa fila = new EventoPendienteJpa();
        fila.id = evento.id();
        fila.tipo = evento.tipo();
        fila.pedidoId = evento.pedidoId();
        fila.cliente = evento.cliente();
        fila.ocurridoEn = evento.ocurridoEn();
        return fila;
    }

    EventoDePedido aEvento() {
        return new EventoDePedido(id, tipo, pedidoId, cliente, ocurridoEn);
    }
}
