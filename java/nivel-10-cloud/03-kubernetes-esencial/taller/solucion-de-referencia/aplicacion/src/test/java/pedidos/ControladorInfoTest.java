package pedidos;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {ControladorInfo.class, ControladorCorreos.class})
@Import(ServicioCorreos.class)
@TestPropertySource(properties = {"app.mensaje=desde-prueba", "app.ambiente=ci"})
class ControladorInfoTest {

    @Autowired
    MockMvc mvc;

    @Test
    void laConfiguracionLlegaPorPropiedades() throws Exception {
        mvc.perform(get("/api/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("desde-prueba"))
                .andExpect(jsonPath("$.ambiente").value("ci"));
    }

    @Test
    void laSesionCuentaLasVisitas() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        mvc.perform(get("/api/visitas").session(sesion)).andExpect(jsonPath("$.visitas").value(1));
        mvc.perform(get("/api/visitas").session(sesion)).andExpect(jsonPath("$.visitas").value(2));
    }

    @Test
    void elCorreoEnColaResponde202() throws Exception {
        mvc.perform(post("/api/correos/asincrono").param("demoraMs", "1"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.estado").value("EN_COLA"));
    }
}
