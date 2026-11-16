import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public final class RequisitoConsultaPrevia implements Requisito {
    @Override
    public Optional<String> incumplimiento(String mascota, LocalDateTime fecha, List<Cita> historial) {
        boolean hayConsulta = historial.stream()
                .anyMatch(c -> c.tipo().nombre().equals(TiposDeCita.CONSULTA.nombre()) && c.fecha().isBefore(fecha));
        return hayConsulta ? Optional.empty() : Optional.of("La cirugía requiere una consulta previa");
    }
}
