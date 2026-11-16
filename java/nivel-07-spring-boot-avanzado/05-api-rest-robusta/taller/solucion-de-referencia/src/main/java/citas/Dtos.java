package citas;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;

/** Entrada: solo formato. Que el horario esté libre es una regla de negocio y no va aquí. */
record CrearCitaRequest(
        @NotNull @Positive Long medicoId,
        @NotNull @Positive Long pacienteId,
        @NotBlank @Size(max = 100) String pacienteNombre,
        @NotNull LocalDateTime inicio,
        @Size(max = 500) String motivo) { }

/** Respuesta v1: el contrato publicado primero. */
record CitaV1(Long id, Long medicoId, String pacienteNombre, String fecha, String hora, EstadoCita estado) {
    static CitaV1 de(Cita c) {
        return new CitaV1(c.getId(), c.getMedicoId(), c.getPacienteNombre(),
                c.getInicio().toLocalDate().toString(), c.getInicio().toLocalTime().toString(), c.getEstado());
    }
}

/** Respuesta v2: cambio incompatible (paciente como objeto, inicio como instante con zona). */
record CitaV2(Long id, Long medicoId, Paciente paciente, OffsetDateTime inicio, EstadoCita estado) {
    record Paciente(Long id, String nombre) { }

    static CitaV2 de(Cita c) {
        return new CitaV2(c.getId(), c.getMedicoId(), new Paciente(c.getPacienteId(), c.getPacienteNombre()),
                c.getInicio().atOffset(ZoneOffset.ofHours(-5)), c.getEstado());
    }
}
