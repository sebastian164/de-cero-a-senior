import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

public class RegistroUsuarios {

    // correo -> [nombre, hash de la contraseña]
    private final Map<String, String[]> usuarios = new HashMap<>();

    public void registrar(String nombre, String correo, String contrasena) {
        // Validaciones
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre es obligatorio");
        }
        if (correo == null || !correo.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("El correo no es válido");
        }
        if (contrasena == null || contrasena.length() < 8) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres");
        }
        if (!contrasena.matches(".*\\d.*")) {
            throw new IllegalArgumentException("La contraseña debe tener al menos un número");
        }
        if (usuarios.containsKey(correo.toLowerCase())) {
            throw new IllegalStateException("Ya existe un usuario con ese correo");
        }

        // Hash de la contraseña (simplificado: NO es apto para producción)
        String hash;
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            hash = HexFormat.of().formatHex(sha.digest(contrasena.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("No hay soporte para SHA-256", e);
        }

        // Almacenamiento
        usuarios.put(correo.toLowerCase(), new String[] {nombre, hash});

        // Correo de bienvenida (simulado)
        String cuerpo = "Hola " + nombre + ",\n"
                + "Tu cuenta fue creada. Ya puedes iniciar sesión con " + correo + ".\n"
                + "Equipo de Soporte";
        System.out.println("Enviando correo a " + correo);
        System.out.println("Asunto: ¡Bienvenido!");
        System.out.println(cuerpo);

        // Auditoría
        System.out.println("[AUDITORIA] " + LocalDateTime.now() + " REGISTRO_USUARIO correo=" + correo);
    }

    public int totalUsuarios() {
        return usuarios.size();
    }
}
