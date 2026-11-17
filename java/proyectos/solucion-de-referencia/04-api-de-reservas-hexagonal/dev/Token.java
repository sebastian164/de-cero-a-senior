import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Herramienta de desarrollo: emite un JWT firmado (RS256) para probar la API a mano. Sin dependencias: solo el JDK.
 *
 *   java dev/Token.java ana            token de un usuario normal
 *   java dev/Token.java admin ADMIN    token de un administrador
 *
 * La primera vez genera el par de claves en dev/ (jwt-privada.pem y jwt-publica.pem). La API lee la pública.
 * Son claves de juguete para tu máquina: nunca las uses ni las subas a un repositorio real.
 */
public class Token {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Uso: java dev/Token.java <usuario> [ADMIN]");
            System.exit(1);
        }
        Path carpeta = Path.of("dev");
        Path privada = carpeta.resolve("jwt-privada.pem"), publica = carpeta.resolve("jwt-publica.pem");
        if (!Files.exists(privada) || !Files.exists(publica)) {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            KeyPair par = g.generateKeyPair();
            Files.createDirectories(carpeta);
            Files.writeString(privada, pem("PRIVATE KEY", par.getPrivate().getEncoded()));
            Files.writeString(publica, pem("PUBLIC KEY", par.getPublic().getEncoded()));
            System.err.println("Claves generadas en " + carpeta.toAbsolutePath());
        }
        String limpia = Files.readString(privada).replaceAll("-----[A-Z ]+-----|\\s", "");
        var clave = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(limpia)));

        long ahora = System.currentTimeMillis() / 1000;
        String roles = args.length > 1 && args[1].equals("ADMIN") ? ",\"roles\":[\"ADMIN\"]" : "";
        String cabecera = b64("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        String carga = b64("{\"sub\":\"" + args[0] + "\",\"iat\":" + ahora + ",\"exp\":" + (ahora + 3600) + roles + "}");
        Signature firma = Signature.getInstance("SHA256withRSA");
        firma.initSign(clave);
        firma.update((cabecera + "." + carga).getBytes(StandardCharsets.US_ASCII));
        System.out.println(cabecera + "." + carga + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(firma.sign()));
    }

    static String b64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    static String pem(String tipo, byte[] der) {
        return "-----BEGIN " + tipo + "-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der) + "\n-----END " + tipo + "-----\n";
    }
}
