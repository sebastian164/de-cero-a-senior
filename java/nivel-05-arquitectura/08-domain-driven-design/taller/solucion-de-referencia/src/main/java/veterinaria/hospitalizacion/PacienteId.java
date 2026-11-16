package veterinaria.hospitalizacion;

public record PacienteId(String valor) {
    public PacienteId { if (valor == null || valor.isBlank()) throw new IllegalArgumentException("paciente sin id"); }
}
