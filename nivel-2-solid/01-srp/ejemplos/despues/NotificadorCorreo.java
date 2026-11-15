/**
 * Razón para cambiar: el canal por el que se le habla al cliente.
 * Quién pide el cambio: servicio al cliente.
 */
public class NotificadorCorreo {

    public void enviar(String destinatario, String asunto, String cuerpo) {
        // Simulado: en un sistema real aquí habría un cliente SMTP.
        System.out.println("Enviando correo a " + destinatario);
        System.out.println("Asunto: " + asunto);
        System.out.println(cuerpo);
    }
}
