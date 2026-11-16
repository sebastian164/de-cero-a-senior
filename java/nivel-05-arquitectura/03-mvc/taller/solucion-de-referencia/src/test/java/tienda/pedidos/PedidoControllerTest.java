package tienda.pedidos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tienda.pedidos.aplicacion.CancelarPedido;
import tienda.pedidos.aplicacion.PedidoNoEncontrado;
import tienda.pedidos.dominio.CancelacionRechazada;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Solo la capa web: el caso de uso es un doble. Se verifica la traducción a HTTP. */
@WebMvcTest
class PedidoControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean CancelarPedido cancelarPedido;

    @Test
    void cancelacionAceptadaEs204() throws Exception {
        mvc.perform(post("/pedidos/7/cancelar")).andExpect(status().isNoContent());
        verify(cancelarPedido).ejecutar(7L);
    }

    @Test
    void reglaIncumplidaEs409() throws Exception {
        doThrow(new CancelacionRechazada("Ya fue enviado")).when(cancelarPedido).ejecutar(7L);
        mvc.perform(post("/pedidos/7/cancelar"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya fue enviado"));
    }

    @Test
    void pedidoInexistenteEs404() throws Exception {
        doThrow(new PedidoNoEncontrado(9L)).when(cancelarPedido).ejecutar(9L);
        mvc.perform(post("/pedidos/9/cancelar")).andExpect(status().isNotFound());
    }

    @Test
    void idNoNumericoEs400() throws Exception {
        mvc.perform(post("/pedidos/abc/cancelar")).andExpect(status().isBadRequest());
    }
}
