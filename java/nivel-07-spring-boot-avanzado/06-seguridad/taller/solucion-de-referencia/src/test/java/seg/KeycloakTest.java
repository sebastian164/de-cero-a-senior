package seg;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Servidor de recursos contra un Keycloak real (se activa con -Dkc.url=http://localhost:42180). */
@EnabledIfSystemProperty(named = "kc.url", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
class KeycloakTest {

    static final String KC = System.getProperty("kc.url");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> KC + "/realms/curso");
        r.add("spring.security.oauth2.resourceserver.jwt.audiences", () -> "api-pedidos");   // valida emisor, expiración y audiencia
        r.add("app.jwt.claim-usuario", () -> "preferred_username");
    }

    @Autowired MockMvc mvc;

    static String token(String usuario) throws Exception { return token("api-curso", usuario); }

    static String token(String cliente, String usuario) throws Exception {
        String cuerpo = "grant_type=password&client_id=" + cliente + "&username=" + usuario + "&password="
                + URLEncoder.encode("curso", StandardCharsets.UTF_8);
        var resp = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(KC + "/realms/curso/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build(),
                HttpResponse.BodyHandlers.ofString());
        var m = Pattern.compile("\"access_token\":\"([^\"]+)\"").matcher(resp.body());
        if (!m.find()) throw new IllegalStateException("sin token: " + resp.body());
        return "Bearer " + m.group(1);
    }

    @Test
    void rolesYPropiedadConTokensDeKeycloak() throws Exception {
        String ana = token("ana"), root = token("root"), sara = token("sara");
        mvc.perform(get("/v1/pedidos/1").header("Authorization", ana)).andExpect(status().isOk());
        mvc.perform(get("/v1/pedidos/3").header("Authorization", ana)).andExpect(status().isForbidden());
        mvc.perform(get("/v1/admin/usuarios").header("Authorization", ana)).andExpect(status().isForbidden());
        mvc.perform(get("/v1/admin/usuarios").header("Authorization", root)).andExpect(status().isOk());
        mvc.perform(get("/v1/pedidos/3").header("Authorization", root)).andExpect(status().isOk());
        mvc.perform(get("/v1/soporte/total").header("Authorization", sara)).andExpect(status().isOk());
        mvc.perform(get("/v1/soporte/total").header("Authorization", ana)).andExpect(status().isForbidden());
        mvc.perform(get("/v1/pedidos/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/pedidos/1").header("Authorization", ana + "x")).andExpect(status().isUnauthorized());
    }

    @Test
    void laAudienciaSeValidaConTokensRealesDeKeycloak() throws Exception {
        // Mismo usuario, mismo emisor y firma válidos; solo cambia el cliente que emitió el token.
        // api-curso lleva aud=api-pedidos (mapper de audiencia); otra-app no, y debe ser rechazado.
        mvc.perform(get("/v1/pedidos/1").header("Authorization", token("api-curso", "ana"))).andExpect(status().isOk());
        mvc.perform(get("/v1/pedidos/1").header("Authorization", token("otra-app", "ana"))).andExpect(status().isUnauthorized());
    }
}
