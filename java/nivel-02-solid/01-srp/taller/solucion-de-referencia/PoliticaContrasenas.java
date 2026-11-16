import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Actor: Seguridad. Qué contraseña se acepta y cómo se protege. */
public class PoliticaContrasenas {

    public void validar(String contrasena) {
        if (contrasena == null || contrasena.length() < 8) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres");
        }
        if (!contrasena.matches(".*\\d.*")) {
            throw new IllegalArgumentException("La contraseña debe tener al menos un número");
        }
    }

    /** Hash simplificado: NO es apto para producción (igual que el original). */
    public String proteger(String contrasena) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(contrasena.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("No hay soporte para SHA-256", e);
        }
    }
}
