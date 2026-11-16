import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Cambio 4: entre dos vacunas de la misma mascota deben pasar más de N días.
 * Se mira la distancia en ambos sentidos: una vacuna ya agendada en el futuro también bloquea.
 */
public final class RequisitoIntervaloVacunas implements Requisito {
    private final String tipoVacuna;
    private final int dias;

    public RequisitoIntervaloVacunas(String tipoVacuna, int dias) {
        this.tipoVacuna = tipoVacuna;
        this.dias = dias;
    }

    @Override
    public Optional<String> incumplimiento(String mascota, LocalDateTime fecha, List<Cita> historial) {
        boolean reciente = historial.stream()
                .filter(c -> c.tipo().nombre().equals(tipoVacuna))
                .anyMatch(c -> Math.abs(ChronoUnit.DAYS.between(c.fecha(), fecha)) <= dias);
        return reciente ? Optional.of("Deben pasar más de " + dias + " días entre dos vacunas") : Optional.empty();
    }
}
