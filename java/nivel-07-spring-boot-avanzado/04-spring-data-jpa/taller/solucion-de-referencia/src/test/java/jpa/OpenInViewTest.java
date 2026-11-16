package jpa;

import jakarta.persistence.EntityManager;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Taller 2. */
class OpenInViewTest {

    static Long primerId(PedidoRepository pedidos) {
        return pedidos.findByEstadoOrderById(EstadoPedido.PENDIENTE).getFirst().getId();
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    class Desactivado {
        @Autowired MockMvc mvc;
        @Autowired PedidoRepository pedidos;
        @Autowired EntityManager em;
        @Autowired TransactionTemplate tx;

        @BeforeEach
        void datos() { Datos.cargar(em, tx); }

        @Test
        void loQueSeRompe() {
            Long id = primerId(pedidos);
            assertThatThrownBy(() -> mvc.perform(get("/antes/pedidos/" + id)))
                    .hasRootCauseInstanceOf(LazyInitializationException.class)
                    .rootCause().hasMessageContaining("no session");
        }

        @Test
        void arregladoEnElCasoDeUso() throws Exception {
            Long id = primerId(pedidos);
            mvc.perform(get("/pedidos/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cliente").value("cliente-1"))
                    .andExpect(jsonPath("$.lineas").value(2));
        }
    }

    @Nested
    @SpringBootTest(properties = "spring.jpa.open-in-view=true")
    @AutoConfigureMockMvc
    class ActivadoLoOculta {
        @Autowired MockMvc mvc;
        @Autowired PedidoRepository pedidos;
        @Autowired EntityManager em;
        @Autowired TransactionTemplate tx;

        @BeforeEach
        void datos() { Datos.cargar(em, tx); }

        @Test
        void funcionaPeroConsultaDesdeLaCapaWeb() throws Exception {
            Long id = primerId(pedidos);
            RegistroSql.limpiar();
            mvc.perform(get("/antes/pedidos/" + id)).andExpect(status().isOk());
            // la segunda consulta (el cliente) la dispara el controlador, fuera de cualquier caso de uso
            assertThat(RegistroSql.selects()).isEqualTo(2);
        }
    }
}
