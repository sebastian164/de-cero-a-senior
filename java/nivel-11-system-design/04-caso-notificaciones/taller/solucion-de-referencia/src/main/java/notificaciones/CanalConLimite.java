package notificaciones;

/** Decorador: cada envío al proveedor pasa antes por su cubeta de fichas, para no superar la cuota que el proveedor concede. */
public class CanalConLimite implements CanalDeEnvio {
    private final CanalDeEnvio destino;
    private final LimitadorDeCubeta limitador;

    public CanalConLimite(CanalDeEnvio destino, LimitadorDeCubeta limitador) { this.destino = destino; this.limitador = limitador; }

    @Override public String nombre() { return destino.nombre(); }

    @Override public void enviar(Notificacion n) { limitador.adquirir(); destino.enviar(n); }
}
