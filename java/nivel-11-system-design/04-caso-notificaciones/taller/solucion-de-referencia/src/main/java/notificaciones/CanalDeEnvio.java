package notificaciones;

/** Puerto hacia un proveedor externo (Adapter/Strategy): el servicio solo conoce esta interfaz. */
public interface CanalDeEnvio {
    /** Falla de la llamada al proveedor: dispara el reintento o la conmutación al respaldo. */
    class FalloDelCanal extends RuntimeException {
        public FalloDelCanal(String mensaje) { super(mensaje); }
    }

    String nombre();

    void enviar(Notificacion n);
}
