package banco.jpa;

import banco.aplicacion.EventoDeCuenta;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Una fila de la bandeja de salida. */
@Entity
@Table(name = "evento_pendiente")
public class EventoPendienteJpa {

    @Id
    private String id;

    @Column(nullable = false)
    private String tipo;

    @Column(name = "cuenta_id", nullable = false)
    private String cuentaId;

    @Column(nullable = false)
    private long centavos;

    @Column(name = "ocurrido_en", nullable = false)
    private Instant ocurridoEn;

    protected EventoPendienteJpa() {
    }

    static EventoPendienteJpa desde(EventoDeCuenta evento) {
        EventoPendienteJpa fila = new EventoPendienteJpa();
        fila.id = evento.id();
        fila.tipo = evento.tipo();
        fila.cuentaId = evento.cuentaId();
        fila.centavos = evento.centavos();
        fila.ocurridoEn = evento.ocurridoEn();
        return fila;
    }
}
