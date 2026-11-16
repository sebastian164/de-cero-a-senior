/** Actor: Marketing. Qué mensaje recibe el usuario (el envío es simulado, como en el original). */
public class CorreoBienvenida {

    public void enviar(String nombre, String correo) {
        String cuerpo = "Hola " + nombre + ",\n"
                + "Tu cuenta fue creada. Ya puedes iniciar sesión con " + correo + ".\n"
                + "Equipo de Soporte";
        System.out.println("Enviando correo a " + correo);
        System.out.println("Asunto: ¡Bienvenido!");
        System.out.println(cuerpo);
    }
}
