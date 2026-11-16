package tienda;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import tienda.despues.TiendaApplication;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TiendaApplication.class)
@AutoConfigureMockMvc
@DirtiesContext
class PedidoApiTest {

    @Autowired MockMvc mvc;

    @Test
    void confirmaYLuegoRechazaPorCupo() throws Exception {
        mvc.perform(post("/pedidos/11/confirmacion")).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADO"))
                .andExpect(jsonPath("$.cliente").value("Ana"));
        // deuda = 700.000 + 200.000; el pedido 12 (500.000) ya no cabe en 1.000.000
        mvc.perform(post("/pedidos/12/confirmacion")).andExpect(status().isUnprocessableContent());
        mvc.perform(get("/pedidos/99")).andExpect(status().isNotFound());
    }
}
