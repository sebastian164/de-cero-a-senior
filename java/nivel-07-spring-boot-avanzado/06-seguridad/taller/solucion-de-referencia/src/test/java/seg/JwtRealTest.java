package seg;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Validación real de tokens: firma, emisor, audiencia y expiración, con un par de claves RSA local. */
@SpringBootTest
@AutoConfigureMockMvc
class JwtRealTest {

    static final KeyPair PAR = generar();
    static final KeyPair OTRO = generar();
    static final String EMISOR = "https://idp.local/realms/curso";
    static final String AUDIENCIA = "api-pedidos";

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        String pem = "-----BEGIN PUBLIC KEY-----\n" + Base64.getMimeEncoder().encodeToString(PAR.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
        r.add("app.jwt.clave-publica-pem", () -> pem);
        r.add("app.jwt.emisor", () -> EMISOR);
        r.add("app.jwt.audiencia", () -> AUDIENCIA);
    }

    @Autowired MockMvc mvc;

    static KeyPair generar() {
        try {
            var g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    static String token(KeyPair clave, String emisor, String audiencia, Instant expira, String sub, List<String> roles) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(emisor).audience(audiencia).subject(sub)
                .issueTime(Date.from(Instant.now().minusSeconds(10))).expirationTime(Date.from(expira))
                .claim("roles", roles).build();
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner((RSAPrivateKey) clave.getPrivate()));
        return jwt.serialize();
    }

    String valido(String sub, String... roles) throws Exception {
        return token(PAR, EMISOR, AUDIENCIA, Instant.now().plusSeconds(300), sub, List.of(roles));
    }

    @Test
    void tokenValidoDeCliente() throws Exception {
        mvc.perform(get("/v1/pedidos/1").header("Authorization", "Bearer " + valido("ana", "CLIENTE"))).andExpect(status().isOk());
        mvc.perform(get("/v1/pedidos/3").header("Authorization", "Bearer " + valido("ana", "CLIENTE"))).andExpect(status().isForbidden());
        mvc.perform(get("/v1/admin/usuarios").header("Authorization", "Bearer " + valido("ana", "CLIENTE"))).andExpect(status().isForbidden());
    }

    @Test
    void tokenValidoDeAdmin() throws Exception {
        mvc.perform(get("/v1/admin/usuarios").header("Authorization", "Bearer " + valido("root", "ADMIN"))).andExpect(status().isOk());
    }

    @Test
    void tokenExpiradoEs401() throws Exception {
        String t = token(PAR, EMISOR, AUDIENCIA, Instant.now().minusSeconds(120), "ana", List.of("CLIENTE"));
        mvc.perform(get("/v1/pedidos/1").header("Authorization", "Bearer " + t)).andExpect(status().isUnauthorized());
    }

    @Test
    void firmaDeOtraClaveEs401() throws Exception {
        String t = token(OTRO, EMISOR, AUDIENCIA, Instant.now().plusSeconds(300), "ana", List.of("CLIENTE"));
        mvc.perform(get("/v1/pedidos/1").header("Authorization", "Bearer " + t)).andExpect(status().isUnauthorized());
    }

    @Test
    void emisorIncorrectoEs401() throws Exception {
        String t = token(PAR, "https://atacante.example", AUDIENCIA, Instant.now().plusSeconds(300), "ana", List.of("CLIENTE"));
        mvc.perform(get("/v1/pedidos/1").header("Authorization", "Bearer " + t)).andExpect(status().isUnauthorized());
    }

    @Test
    void audienciaIncorrectaEs401() throws Exception {
        String t = token(PAR, EMISOR, "otra-api", Instant.now().plusSeconds(300), "ana", List.of("CLIENTE"));
        mvc.perform(get("/v1/pedidos/1").header("Authorization", "Bearer " + t)).andExpect(status().isUnauthorized());
    }

    @Test
    void sinTokenOTokenBasuraEs401() throws Exception {
        mvc.perform(get("/v1/pedidos/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/pedidos/1").header("Authorization", "Bearer basura")).andExpect(status().isUnauthorized());
    }
}
