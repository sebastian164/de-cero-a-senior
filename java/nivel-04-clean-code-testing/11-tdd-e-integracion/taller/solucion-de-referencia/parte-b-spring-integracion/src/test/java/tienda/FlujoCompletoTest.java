package tienda;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// Parte B.3 del taller: contexto completo, controlador + servicio + JPA + PostgreSQL real, sin dobles.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FlujoCompletoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mvc;

    @Test
    void crearConsultarEntregarYNoPoderEntregarDeNuevo() throws Exception {
        String cuerpo = "{\"cliente\":\"ana\",\"items\":[{\"producto\":\"teclado\",\"cantidad\":2,\"precioUnitario\":50000}]}";

        String respuesta = mvc.perform(post("/pedidos").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.total").value(100_000))
                .andReturn().getResponse().getContentAsString();
        // El id lo asigna la base de datos: no se supone que este pedido sea el primero del contenedor.
        Number id = JsonPath.read(respuesta, "$.id");

        mvc.perform(get("/pedidos/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.cliente").value("ana"));
        mvc.perform(post("/pedidos/" + id + "/entrega")).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ENTREGADO"));
        mvc.perform(post("/pedidos/" + id + "/entrega")).andExpect(status().isUnprocessableContent());
        mvc.perform(get("/pedidos/999999")).andExpect(status().isNotFound());
    }
}
