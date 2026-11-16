import java.util.HashMap;
import java.util.Map;

/** Actor: Plataforma. Dónde y cómo se guardan los usuarios. */
public class AlmacenUsuarios {

    // correo normalizado -> [nombre, hash de la contraseña]
    private final Map<String, String[]> usuarios = new HashMap<>();

    public boolean existe(String correo) {
        return usuarios.containsKey(correo.toLowerCase());
    }

    public void guardar(String nombre, String correo, String hash) {
        usuarios.put(correo.toLowerCase(), new String[] {nombre, hash});
    }

    public int total() {
        return usuarios.size();
    }
}
