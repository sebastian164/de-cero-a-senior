package tienda.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tienda.ContratoDelInventario;
import tienda.ContratoDelRepositorioDePedidos;
import tienda.aplicacion.puertos.salida.UnidadDeTrabajo;

/** Paso 2 del taller 08: los mismos contratos que pasa AlmacenEnMemoria, contra AlmacenJpa y PostgreSQL 17 (Testcontainers). */
@SpringBootTest(classes = AplicacionTienda.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ContratosJpaTest {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("tienda").withUsername("banco").withPassword("banco");

    static {
        POSTGRES.start();
        Runtime.getRuntime().addShutdownHook(new Thread(POSTGRES::stop));
    }

    @DynamicPropertySource
    static void baseDeDatos(DynamicPropertyRegistry propiedades) {
        propiedades.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        propiedades.add("logging.level.org.hibernate.SQL", () -> "info");
    }

    @Autowired
    AlmacenJpa almacen;

    @Autowired
    UnidadDeTrabajo unidadDeTrabajo;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void elRepositorioDePedidosJpaCumpleElContrato() {
        List<String> incumplidas = ContratoDelRepositorioDePedidos.incumplimientos(
                () -> new ContratoDelRepositorioDePedidos.Escenario(almacen, unidadDeTrabajo));
        System.out.println("Contrato de pedidos contra JPA: " + incumplidas);
        assertEquals(List.of(), incumplidas);
    }

    @Test
    void elInventarioJpaCumpleElContrato() {
        String producto = "contrato-" + UUID.randomUUID();
        jdbc.update("insert into producto (id, precio_centavos) values (?, 100)", producto);   // la clave foránea lo exige
        List<String> incumplidas = ContratoDelInventario.incumplimientos(
                () -> new ContratoDelInventario.Escenario(almacen, unidadDeTrabajo, producto));
        System.out.println("Contrato de inventario contra JPA: " + incumplidas);
        assertEquals(List.of(), incumplidas);
    }
}
