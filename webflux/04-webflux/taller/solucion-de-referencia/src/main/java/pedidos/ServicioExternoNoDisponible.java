package pedidos;
public class ServicioExternoNoDisponible extends RuntimeException {
    public ServicioExternoNoDisponible(String servicio, Throwable causa) { super("el servicio de " + servicio + " no está disponible o tardó demasiado", causa); }
}
