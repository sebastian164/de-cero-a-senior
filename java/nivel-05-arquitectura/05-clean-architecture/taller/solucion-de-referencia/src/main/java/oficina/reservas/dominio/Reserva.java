package oficina.reservas.dominio;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;

/** Entidad con las reglas que existirían aunque no hubiera software. */
public class Reserva {

    public static final Duration MINIMA = Duration.ofMinutes(30);
    public static final Duration MAXIMA = Duration.ofHours(4);
    public static final Duration ANTELACION_PARA_CANCELAR = Duration.ofHours(1);

    public enum Estado { ACTIVA, CANCELADA }

    private final ReservaId id;
    private final SalaId sala;
    private final String usuario;
    private final Intervalo intervalo;
    private Estado estado;

    public Reserva(ReservaId id, SalaId sala, String usuario, Intervalo intervalo, Estado estado) {
        this.id = id;
        this.sala = sala;
        this.usuario = usuario;
        this.intervalo = intervalo;
        this.estado = estado;
    }

    /** Fábrica: valida duración y solapamiento contra las reservas activas de la sala. */
    public static Reserva nueva(SalaId sala, String usuario, Intervalo intervalo, Collection<Reserva> existentesDeLaSala) {
        Duration d = intervalo.duracion();
        if (d.compareTo(MINIMA) < 0 || d.compareTo(MAXIMA) > 0) {
            throw new ReglaIncumplida("La reserva debe durar entre 30 minutos y 4 horas");
        }
        boolean choca = existentesDeLaSala.stream()
                .filter(Reserva::activa)
                .anyMatch(r -> r.intervalo.seSolapaCon(intervalo));
        if (choca) throw new ReglaIncumplida("La sala ya está reservada en ese horario");
        return new Reserva(ReservaId.nueva(), sala, usuario, intervalo, Estado.ACTIVA);
    }

    public void cancelar(String quien, LocalDateTime ahora) {
        if (!usuario.equals(quien)) throw new ReglaIncumplida("Solo quien reservó puede cancelar");
        if (ahora.plus(ANTELACION_PARA_CANCELAR).isAfter(intervalo.inicio())) {
            throw new ReglaIncumplida("Solo se cancela hasta una hora antes del inicio");
        }
        if (estado == Estado.CANCELADA) throw new ReglaIncumplida("La reserva ya estaba cancelada");
        estado = Estado.CANCELADA;
    }

    public boolean activa() { return estado == Estado.ACTIVA; }
    public ReservaId id() { return id; }
    public SalaId sala() { return sala; }
    public String usuario() { return usuario; }
    public Intervalo intervalo() { return intervalo; }
    public Estado estado() { return estado; }
}
