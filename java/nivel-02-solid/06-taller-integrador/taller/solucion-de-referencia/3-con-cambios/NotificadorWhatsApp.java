/** Adaptador: WhatsApp simulado por consola. */
public final class NotificadorWhatsApp implements Notificador {
    @Override
    public void notificar(Dueno dueno, String mensaje) {
        System.out.println("[WHATSAPP a " + dueno.telefono() + "] " + mensaje);
    }
}
