package oficina.reservas.dominio;

import java.time.Duration;
import java.time.LocalDateTime;

/** Objeto de valor: intervalo semiabierto [inicio, fin). */
public record Intervalo(LocalDateTime inicio, LocalDateTime fin) {
    public Intervalo {
        if (!fin.isAfter(inicio)) throw new IllegalArgumentException("el fin debe ser posterior al inicio");
    }

    public Duration duracion() { return Duration.between(inicio, fin); }

    public boolean seSolapaCon(Intervalo otro) {
        return inicio.isBefore(otro.fin) && otro.inicio.isBefore(fin);
    }
}
