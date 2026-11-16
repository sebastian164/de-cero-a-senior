package migraciones;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import soporte.Entorno;

/**
 * Pasos 1 y 6: Flyway con tres migraciones en Spring Boot, aplicadas a una base recién creada.
 * Es la misma prueba que ejecuta la integración continua (.github/workflows/migraciones.yml).
 * Si alguna migración falla, o si las entidades no coinciden con el esquema (ddl-auto=validate), el contexto no arranca.
 */
@SpringBootTest(classes = AplicacionTienda.class)
class MigracionesEnBaseLimpiaTest {

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry r) {
        String url = Entorno.baseNueva("n8_05_limpia");
        r.add("spring.datasource.url", () -> url);
        r.add("spring.datasource.username", () -> Entorno.USUARIO);
        r.add("spring.datasource.password", () -> Entorno.CLAVE);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PedidoRepositorio pedidos;

    @Test
    void las_tres_migraciones_se_aplican_y_hibernate_valida_el_esquema() {
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class))
                .containsExactly("1", "2", "3");
        jdbc.update("INSERT INTO clientes (documento, nombre) VALUES ('1', 'Ana')");
        Long cliente = jdbc.queryForObject("SELECT id FROM clientes", Long.class);
        Pedido p = pedidos.save(new Pedido(cliente, new BigDecimal("10.00")));
        assertThat(pedidos.findById(p.getId()).orElseThrow().getEstado()).isEqualTo("CREADO");
    }
}
