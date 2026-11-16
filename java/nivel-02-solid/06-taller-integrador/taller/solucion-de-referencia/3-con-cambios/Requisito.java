import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Una condición para poder agendar un tipo de cita. Devuelve el motivo si no se cumple. */
public interface Requisito {
    Optional<String> incumplimiento(String mascota, LocalDateTime fecha, List<Cita> historialMascota);
}
