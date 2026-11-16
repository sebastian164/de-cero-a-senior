import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Una sola implementación para las cinco interfaces. */
public class ServicioUsuariosEnMemoria implements RegistroDeUsuarios, PerfilDeUsuario, AdministracionDeUsuarios,
        CampanasDeMarketing, MetricasDeUsuarios {

    private final Map<String, String> usuarios = new LinkedHashMap<>();
    private final Map<String, String> contrasenas = new HashMap<>();
    private final Set<String> bloqueados = new HashSet<>();
    private final List<String> enviados = new java.util.ArrayList<>();

    @Override public void registrar(String usuario, String correo) { usuarios.put(usuario, correo); }
    @Override public String buscar(String usuario) { return usuarios.get(usuario); }
    @Override public List<String> listarTodos() { return List.copyOf(usuarios.keySet()); }

    @Override
    public void cambiarContrasena(String usuario, String nueva) {
        exigirExistente(usuario);
        contrasenas.put(usuario, nueva);
    }

    @Override
    public void bloquear(String usuario) {
        exigirExistente(usuario);
        bloqueados.add(usuario);
    }

    @Override
    public void eliminar(String usuario) {
        usuarios.remove(usuario);
        bloqueados.remove(usuario);
        contrasenas.remove(usuario);
    }

    @Override public String exportarCsv() { return String.join("\n", usuarios.keySet()); }

    @Override
    public void enviarCorreoMasivo(String asunto, String cuerpo) {
        for (String usuario : usuarios.keySet()) {
            if (!bloqueados.contains(usuario)) enviados.add(usuarios.get(usuario) + ": " + asunto);
        }
    }

    /** Simplificación: sin registro de actividad, "activo" es "no bloqueado". */
    @Override public int contarActivosUltimoMes() { return usuarios.size() - bloqueados.size(); }

    boolean estaBloqueado(String usuario) { return bloqueados.contains(usuario); }
    List<String> correosEnviados() { return List.copyOf(enviados); }

    private void exigirExistente(String usuario) {
        if (!usuarios.containsKey(usuario)) throw new IllegalArgumentException("No existe " + usuario);
    }
}
