package tienda;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Comprueba lo que la lección 07 dice de ManejadorDeErrores: 404 y 422 con Problem Details (RFC 9457), sin try/catch en el controlador. */
@WebMvcTest(PedidoController.class)
class ManejadorDeErroresTest {

    @Autowired MockMvc mvc;
    @MockitoBean ServicioPedidos servicio;

    @Test
    void unRecursoInexistenteSeTraduceA404ConProblemDetail() throws Exception {
        when(servicio.buscar(7)).thenThrow(new RecursoNoEncontradoException("No existe el pedido 7"));

        mvc.perform(get("/pedidos/7"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("No existe el pedido 7"));
    }

    @Test
    void unaReglaDeNegocioRotaSeTraduceA422ConProblemDetail() throws Exception {
        when(servicio.entregar(3)).thenThrow(new ReglaDeNegocioException("El pedido 3 ya fue entregado"));

        mvc.perform(post("/pedidos/3/entrega"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("El pedido 3 ya fue entregado"));
    }
}
