package veterinaria.hospitalizacion;

import java.time.LocalDateTime;

public sealed interface EventoDeDominio {
    record PacienteHospitalizado(HospitalizacionId id, PacienteId paciente, LocalDateTime cuando) implements EventoDeDominio { }
    record DosisAdministrada(HospitalizacionId id, String medicamento, Dosis dosis, String enfermero, LocalDateTime cuando) implements EventoDeDominio { }
    record AltaOtorgada(HospitalizacionId id, PacienteId paciente, LocalDateTime cuando) implements EventoDeDominio { }
}
