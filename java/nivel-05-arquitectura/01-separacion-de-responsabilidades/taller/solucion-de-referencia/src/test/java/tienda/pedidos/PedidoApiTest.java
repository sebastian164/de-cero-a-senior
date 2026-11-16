package tienda.pedidos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import tienda.pedidos.adaptadores.mensajeria.ConsumidorPedidos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PedidoApiTest {

    @Autowired MockMvc mvc;
    @Autowired ConsumidorPedidos consumidor;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCreaElPedidoConDescuento() throws Exception {
        mvc.perform(post("/pedidos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cliente\":\"ana\",\"cantidad\":30}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(1_080_000))
                .andExpect(jsonPath("$.mensaje").value("Creado"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void sinRolAdminEs403() throws Exception {
        mvc.perform(post("/pedidos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cliente\":\"ana\",\"cantidad\":30}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void sinAutenticarEs401() throws Exception {
        mvc.perform(post("/pedidos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cliente\":\"ana\",\"cantidad\":30}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void formatoInvalidoEs400() throws Exception {
        mvc.perform(post("/pedidos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cliente\":\"\",\"cantidad\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elCanalDeMensajeriaReutilizaLaMismaRegla() {
        var pedido = consumidor.alRecibir("luis;30");
        assertThat(pedido.total()).isEqualTo(1_080_000);
        assertThat(pedido.id()).isNotNull();
    }
}
