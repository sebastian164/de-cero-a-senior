import java.time.LocalDateTime;

/** Una cita registrada, con el precio que se cobró. */
public record Cita(String mascota, Dueno dueno, TipoCita tipo, LocalDateTime fecha, long precio) {
    public Cita {
        if (mascota == null || mascota.isBlank()) throw new IllegalArgumentException("Falta la mascota");
        if (dueno == null || tipo == null || fecha == null) throw new IllegalArgumentException("Faltan datos de la cita");
    }
}
