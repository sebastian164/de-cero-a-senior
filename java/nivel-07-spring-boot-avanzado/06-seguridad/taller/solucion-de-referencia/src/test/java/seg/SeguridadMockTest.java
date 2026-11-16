package seg;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** Pruebas con @WithMockUser: permitido, 401 sin autenticar, 403 sin permiso, y propiedad del recurso. */
@SpringBootTest
@AutoConfigureMockMvc
class SeguridadMockTest {

    @Autowired MockMvc mvc;
    @org.springframework.test.context.bean.override.mockito.MockitoBean org.springframework.security.oauth2.jwt.JwtDecoder decodificador;

    @Test
    void rutaPublicaSinCredenciales() throws Exception {
        mvc.perform(get("/v1/productos")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void sinAutenticarEs401() throws Exception {
        mvc.perform(get("/v1/pedidos/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/admin/usuarios")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "ana", roles = "CLIENTE")
    void autenticadoPeroSinRolEs403() throws Exception {
        mvc.perform(get("/v1/admin/usuarios")).andExpect(status().isForbidden());
        mvc.perform(get("/v1/soporte/total")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "ana", roles = "CLIENTE")
    void elClienteVeSuPedido() throws Exception {
        mvc.perform(get("/v1/pedidos/1")).andExpect(status().isOk()).andExpect(jsonPath("$.descripcion").value("Teclado"))
                .andExpect(jsonPath("$.dueno").doesNotExist());
    }

    @Test
    @WithMockUser(username = "ana", roles = "CLIENTE")
    void elClienteNoVeElPedidoDeOtro() throws Exception {
        // El pedido 3 existe y es de luis: ana recibe 403, no 200.
        mvc.perform(get("/v1/pedidos/3")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "ana", roles = "CLIENTE")
    void pedidoInexistenteNoDistingueDeAjeno() throws Exception {
        // Para un cliente, un id inexistente y uno ajeno responden igual (403): no se filtra qué ids existen.
        mvc.perform(get("/v1/pedidos/999")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "ana", roles = "CLIENTE")
    void listaSoloLosPropios() throws Exception {
        mvc.perform(get("/v1/pedidos")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    @WithMockUser(username = "root", roles = "ADMIN")
    void adminVeTodoYEntraAAdmin() throws Exception {
        mvc.perform(get("/v1/pedidos/3")).andExpect(status().isOk());
        mvc.perform(get("/v1/admin/usuarios")).andExpect(status().isOk());
        mvc.perform(get("/v1/pedidos/999")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "sara", roles = "SOPORTE")
    void soporteEntraASoporteNoAAdmin() throws Exception {
        mvc.perform(get("/v1/soporte/total")).andExpect(status().isOk());
        mvc.perform(get("/v1/admin/usuarios")).andExpect(status().isForbidden());
        // Soporte no es dueño ni ADMIN: tampoco ve pedidos ajenos.
        mvc.perform(get("/v1/pedidos/1")).andExpect(status().isForbidden());
    }

    @Test
    void jwtDeMockMvcConAutoridades() throws Exception {
        // jwt() no pasa por nuestro convertidor: se le dan las autoridades ya resueltas (la conversión del claim se prueba en JwtRealTest).
        mvc.perform(get("/v1/admin/usuarios").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isOk());
        mvc.perform(get("/v1/admin/usuarios").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENTE")))).andExpect(status().isForbidden());
    }
}
