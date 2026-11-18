package pedidos.aplicacion;

public final class Errores {
    private Errores() {}
    public static class PagoRechazado extends RuntimeException { public PagoRechazado(String id) { super("pago rechazado para el pedido " + id); } }
    public static class SinExistenciasEnInventario extends RuntimeException { public SinExistenciasEnInventario(String id) { super("el inventario no puede reservar el pedido " + id); } }
    public static class ServicioExternoNoDisponible extends RuntimeException {
        public ServicioExternoNoDisponible(String servicio, Throwable causa) { super("el servicio de " + servicio + " no está disponible o tardó demasiado", causa); }
    }
}
