package jpa;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Taller 1, tercera corrección: carga por lotes. La consulta ingenua no cambia; cambia la propiedad. */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.default_batch_fetch_size=25")
class CargaPorLotesTest {

    @Autowired PedidoRepository pedidos;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;

    @BeforeEach
    void datos() {
        Datos.cargar(em, tx);
    }

    @Test
    void cargaPorLotesHace1MasNSobreTamanoDeLote() {
        tx.executeWithoutResult(s -> {
            List<Pedido> lista = pedidos.findByEstadoOrderById(EstadoPedido.PENDIENTE);
            assertThat(lista.stream().map(p -> p.getCliente().getNombre())).hasSize(40);
        });
        // 1 de pedidos + 40 clientes en lotes de 25 => 2
        assertThat(RegistroSql.selects()).isEqualTo(3);
    }
}
