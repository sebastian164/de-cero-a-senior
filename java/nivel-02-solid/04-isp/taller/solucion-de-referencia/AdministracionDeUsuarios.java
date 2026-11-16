/** Cliente: panel de administración. */
public interface AdministracionDeUsuarios {
    java.util.List<String> listarTodos();
    void bloquear(String usuario);
    void eliminar(String usuario);
}
