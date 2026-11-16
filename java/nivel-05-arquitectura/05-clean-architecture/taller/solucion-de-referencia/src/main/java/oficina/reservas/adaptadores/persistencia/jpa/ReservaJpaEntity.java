package oficina.reservas.adaptadores.persistencia.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reservas")
class ReservaJpaEntity {
    @Id UUID id;
    String sala;
    String usuario;
    LocalDateTime inicio;
    LocalDateTime fin;
    String estado;

    protected ReservaJpaEntity() { }

    ReservaJpaEntity(UUID id, String sala, String usuario, LocalDateTime inicio, LocalDateTime fin, String estado) {
        this.id = id;
        this.sala = sala;
        this.usuario = usuario;
        this.inicio = inicio;
        this.fin = fin;
        this.estado = estado;
    }
}
