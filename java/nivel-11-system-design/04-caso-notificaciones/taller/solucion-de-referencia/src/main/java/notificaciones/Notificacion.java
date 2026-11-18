package notificaciones;

public record Notificacion(String id, String claveIdempotencia, Canal canal, Prioridad prioridad, String destino, long creadaNanos) {
    public enum Canal { EMAIL, SMS, PUSH }
    public enum Prioridad { ALTA, NORMAL }
}
