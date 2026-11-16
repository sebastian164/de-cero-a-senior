package tienda;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// Copiado tal cual de la lección 11; se agregaron package, imports y los ayudantes pedidoPendienteDe / pedidoEntregadoDe.
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PedidoRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired PedidoRepository repositorio;

    @Test
    void encuentraLosPedidosPendientesDeUnCliente() {
        repositorio.save(pedidoPendienteDe("ana"));
        repositorio.save(pedidoEntregadoDe("ana"));

        assertThat(repositorio.findPendientesPorCliente("ana")).hasSize(1);
    }

    // Prueba propia del taller (Parte B.1): la consulta filtra por cliente, filtra por estado y ordena por fecha.
    @Test
    void devuelveSoloLosPendientesDelClienteOrdenadosPorFecha() {
        Instant antigua = Instant.parse("2026-01-05T10:00:00Z");
        Instant reciente = Instant.parse("2026-01-20T10:00:00Z");
        repositorio.save(new Pedido("ana", 1, reciente));
        repositorio.save(new Pedido("ana", 2, antigua));
        repositorio.save(pedidoEntregadoDe("ana"));
        repositorio.save(pedidoPendienteDe("luis"));

        assertThat(repositorio.findPendientesPorCliente("ana"))
                .extracting(Pedido::getCreadoEn)
                .containsExactly(antigua, reciente);
    }

    private static Pedido pedidoPendienteDe(String cliente) {
        return new Pedido(cliente, 100_000, Instant.parse("2026-01-10T10:00:00Z"));
    }

    private static Pedido pedidoEntregadoDe(String cliente) {
        Pedido pedido = new Pedido(cliente, 50_000, Instant.parse("2026-01-11T10:00:00Z"));
        pedido.entregar();
        return pedido;
    }
}
