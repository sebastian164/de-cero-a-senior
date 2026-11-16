/** Adaptador: correo simulado por consola. */
public final class NotificadorCorreo implements Notificador {
    @Override
    public void notificar(Dueno dueno, String mensaje) {
        System.out.println("[CORREO a " + dueno.correo() + "] " + mensaje);
    }
}
