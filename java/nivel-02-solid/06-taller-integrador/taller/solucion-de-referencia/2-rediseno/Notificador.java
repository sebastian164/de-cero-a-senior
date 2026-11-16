/** Puerto de aviso al dueño. El negocio no sabe si es correo, SMS o WhatsApp. */
public interface Notificador {
    void notificar(Dueno dueno, String mensaje);
}
