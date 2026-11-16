package tienda.pedidos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tienda.pedidos.dominio.EstadoPedido;
import tienda.pedidos.dominio.Pedido;
import tienda.pedidos.memoria.NotificadorEnMemoria;
import tienda.pedidos.memoria.RepositorioPedidosEnMemoria;
import tienda.pedidos.mensajeria.ConsumidorCancelaciones;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP y mensajería ejecutan el mismo caso de uso y las mismas reglas. */
@SpringBootTest
@AutoConfigureMockMvc
class DosCanalesTest {

    @Autowired MockMvc mvc;
    @Autowired ConsumidorCancelaciones consumidor;
    @Autowired RepositorioPedidosEnMemoria repositorio;
    @Autowired NotificadorEnMemoria correo;

    @Test
    void ambosCanalesCompartenReglas() throws Exception {
        repositorio.guardar(new Pedido(1, "ana@x.co", LocalDateTime.now().minusHours(1), EstadoPedido.CREADO));
        repositorio.guardar(new Pedido(2, "luis@x.co", LocalDateTime.now().minusHours(1), EstadoPedido.CREADO));
        repositorio.guardar(new Pedido(3, "eva@x.co", LocalDateTime.now().minusHours(1), EstadoPedido.ENVIADO));

        mvc.perform(post("/pedidos/1/cancelar")).andExpect(status().isNoContent());
        consumidor.alRecibir("2");
        consumidor.alRecibir("3");

        assertThat(repositorio.buscar(1).orElseThrow().estado()).isEqualTo(EstadoPedido.CANCELADO);
        assertThat(repositorio.buscar(2).orElseThrow().estado()).isEqualTo(EstadoPedido.CANCELADO);
        assertThat(consumidor.rechazos).containsExactly("3: Ya fue enviado");
        assertThat(correo.enviados).containsExactly("ana@x.co: Pedido cancelado", "luis@x.co: Pedido cancelado");
    }
}
