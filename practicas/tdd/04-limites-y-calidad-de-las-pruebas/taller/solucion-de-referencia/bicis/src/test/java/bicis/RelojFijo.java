package bicis;

import java.time.LocalDateTime;

/** Sustituto: un reloj que da la hora que la prueba le diga. */
final class RelojFijo implements Reloj {

    private LocalDateTime hora = LocalDateTime.of(2026, 11, 15, 0, 0);

    void fijar(LocalDateTime hora) {
        this.hora = hora;
    }

    @Override
    public LocalDateTime ahora() {
        return hora;
    }
}
