/** Actor: Producto. Qué datos se piden y qué hace válido a un usuario. */
public class ValidadorDatosUsuario {

    public void validar(String nombre, String correo) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre es obligatorio");
        }
        if (correo == null || !correo.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("El correo no es válido");
        }
    }
}
