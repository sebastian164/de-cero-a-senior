/** Cliente: módulo de perfil. */
public interface PerfilDeUsuario {
    String buscar(String usuario);
    void cambiarContrasena(String usuario, String nueva);
}
