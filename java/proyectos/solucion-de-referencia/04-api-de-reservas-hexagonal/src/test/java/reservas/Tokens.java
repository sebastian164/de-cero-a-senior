package reservas;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/** Emite JWT de prueba con una clave RSA generada al vuelo; la API solo conoce la clave pública (un archivo PEM). */
final class Tokens {
    private static final KeyPair CLAVE = nueva();
    private static final KeyPair OTRA = nueva();

    private Tokens() { }

    private static KeyPair nueva() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Escribe la clave pública en PEM y devuelve la propiedad que la declara (la lee application.yaml). */
    static String propiedadClavePublica() throws Exception {
        Path pem = Path.of("target/jwt-publica.pem").toAbsolutePath();
        Files.createDirectories(pem.getParent());
        String b64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(CLAVE.getPublic().getEncoded());
        Files.writeString(pem, "-----BEGIN PUBLIC KEY-----\n" + b64 + "\n-----END PUBLIC KEY-----\n");
        return "JWT_CLAVE_PUBLICA=file:" + pem;
    }

    static String token(String usuario, String rol) { return firmar(CLAVE, usuario, rol, 3600); }

    static String tokenDeOtraClave(String usuario, String rol) { return firmar(OTRA, usuario, rol, 3600); }

    static String tokenVencido(String usuario) { return firmar(CLAVE, usuario, null, -3600); }

    private static String firmar(KeyPair clave, String usuario, String rol, long segundosDeVida) {
        try {
            var claims = new JWTClaimsSet.Builder().subject(usuario).issueTime(new Date())
                    .expirationTime(new Date(System.currentTimeMillis() + segundosDeVida * 1000));
            if (rol != null) claims.claim("roles", List.of(rol));
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
            jwt.sign(new RSASSASigner((RSAPrivateKey) clave.getPrivate()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
