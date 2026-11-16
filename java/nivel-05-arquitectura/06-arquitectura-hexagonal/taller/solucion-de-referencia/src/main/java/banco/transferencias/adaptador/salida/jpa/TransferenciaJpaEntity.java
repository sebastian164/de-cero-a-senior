package banco.transferencias.adaptador.salida.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transferencias")
class TransferenciaJpaEntity {
    @Id UUID id;
    String origen;
    String destino;
    long centavos;
    LocalDateTime fecha;
    boolean aceptada;
    String motivoRechazo;

    protected TransferenciaJpaEntity() { }
    TransferenciaJpaEntity(UUID id, String origen, String destino, long centavos, LocalDateTime fecha, boolean aceptada, String motivo) {
        this.id = id; this.origen = origen; this.destino = destino; this.centavos = centavos;
        this.fecha = fecha; this.aceptada = aceptada; this.motivoRechazo = motivo;
    }
}
