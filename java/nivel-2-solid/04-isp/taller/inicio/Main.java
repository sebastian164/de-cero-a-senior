import java.util.List;

public class Main {

    public static void main(String[] args) {
        ServicioUsuarios servicio = new ServicioUsuariosEnMemoria();
        servicio.registrar("ana", "ana@correo.example");
        System.out.println(servicio.buscar("ana"));
        System.out.println(servicio.listarTodos());
    }
}

interface ServicioUsuarios {
    void registrar(String usuario, String correo);
    String buscar(String usuario);
    List<String> listarTodos();
    void cambiarContrasena(String usuario, String nueva);
    void bloquear(String usuario);
    void eliminar(String usuario);
    String exportarCsv();
    void enviarCorreoMasivo(String asunto, String cuerpo);
    int contarActivosUltimoMes();
}

class ServicioUsuariosEnMemoria implements ServicioUsuarios {
    private final java.util.Map<String, String> usuarios = new java.util.LinkedHashMap<>();

    @Override public void registrar(String usuario, String correo) { usuarios.put(usuario, correo); }
    @Override public String buscar(String usuario) { return usuarios.get(usuario); }
    @Override public List<String> listarTodos() { return List.copyOf(usuarios.keySet()); }
    @Override public void cambiarContrasena(String usuario, String nueva) { }
    @Override public void bloquear(String usuario) { }
    @Override public void eliminar(String usuario) { usuarios.remove(usuario); }
    @Override public String exportarCsv() { return String.join("\n", usuarios.keySet()); }
    @Override public void enviarCorreoMasivo(String asunto, String cuerpo) { }
    @Override public int contarActivosUltimoMes() { return usuarios.size(); }
}
