package citas;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
public class Cita {
    @Id @GeneratedValue
    private Long id;
    private Long medicoId;
    private Long pacienteId;
    private String pacienteNombre;
    private LocalDateTime inicio;
    private String motivo;
    @Enumerated(EnumType.STRING)
    private EstadoCita estado = EstadoCita.PROGRAMADA;

    protected Cita() { }

    Cita(Long medicoId, Long pacienteId, String pacienteNombre, LocalDateTime inicio, String motivo) {
        this.medicoId = medicoId;
        this.pacienteId = pacienteId;
        this.pacienteNombre = pacienteNombre;
        this.inicio = inicio;
        this.motivo = motivo;
    }

    /** Regla de negocio: vive en el dominio, no en anotaciones del DTO. */
    void cancelar(LocalDateTime ahora) {
        if (estado == EstadoCita.CANCELADA) throw new ErrorDeApi.Conflicto("La cita ya está cancelada");
        if (Duration.between(ahora, inicio).compareTo(Duration.ofHours(24)) < 0)
            throw new ErrorDeApi.ReglaDeNegocio("cancelacion-tardia", "Cancelación tardía",
                    "Solo se puede cancelar hasta 24 horas antes de la cita");
        estado = EstadoCita.CANCELADA;
    }

    public Long getId() { return id; }
    public Long getMedicoId() { return medicoId; }
    public Long getPacienteId() { return pacienteId; }
    public String getPacienteNombre() { return pacienteNombre; }
    public LocalDateTime getInicio() { return inicio; }
    public String getMotivo() { return motivo; }
    public EstadoCita getEstado() { return estado; }
}
