package reservas.dominio;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public final class Reserva {
    public enum Estado { ACTIVA, CANCELADA }

    /** Política: se cancela sin problema hasta 24 horas antes del inicio. */
    public static final Duration PLAZO_DE_CANCELACION = Duration.ofHours(24);

    private final UUID id;
    private final String espacio;
    private final String usuario;
    private final Franja franja;
    private Estado estado;

    public Reserva(UUID id, String espacio, String usuario, Franja franja, Estado estado) {
        this.id = id; this.espacio = espacio; this.usuario = usuario; this.franja = franja; this.estado = estado;
    }

    public static Reserva nueva(String espacio, String usuario, Franja franja, Instant ahora) {
        if (espacio == null || espacio.isBlank()) throw new IllegalArgumentException("El espacio es obligatorio");
        if (!franja.inicio().isAfter(ahora)) throw new IllegalArgumentException("No se reserva en el pasado");
        return new Reserva(UUID.randomUUID(), espacio, usuario, franja, Estado.ACTIVA);
    }

    public void cancelar(Instant ahora) {
        if (estado == Estado.CANCELADA) return;
        if (ahora.isAfter(franja.inicio().minus(PLAZO_DE_CANCELACION))) throw new CancelacionFueraDePlazo(PLAZO_DE_CANCELACION);
        estado = Estado.CANCELADA;
    }

    /** Dos reservas chocan si son del mismo espacio, las dos están activas y sus franjas se solapan. */
    public boolean chocaCon(Reserva otra) {
        return espacio.equals(otra.espacio) && estado == Estado.ACTIVA && otra.estado == Estado.ACTIVA && franja.seSolapaCon(otra.franja);
    }

    public boolean esDe(String quien) { return usuario.equals(quien); }

    public UUID id() { return id; }
    public String espacio() { return espacio; }
    public String usuario() { return usuario; }
    public Franja franja() { return franja; }
    public Estado estado() { return estado; }

    // Errores de dominio
    public static class FranjaOcupada extends RuntimeException {
        public FranjaOcupada(String espacio) { super("La franja ya está reservada en " + espacio); }
    }
    public static class CancelacionFueraDePlazo extends RuntimeException {
        public CancelacionFueraDePlazo(Duration plazo) { super("Solo se cancela hasta " + plazo.toHours() + " horas antes del inicio"); }
    }
    public static class NoEncontrada extends RuntimeException {
        public NoEncontrada(UUID id) { super("No existe la reserva " + id); }
    }
    public static class SinPermiso extends RuntimeException {
        public SinPermiso() { super("Esa reserva no es tuya"); }
    }
}
