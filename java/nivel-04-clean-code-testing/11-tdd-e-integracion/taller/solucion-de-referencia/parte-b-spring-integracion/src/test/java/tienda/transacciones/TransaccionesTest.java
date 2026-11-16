package tienda.transacciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.UnexpectedRollbackException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tienda.AplicacionTienda;

/** Comprueba, contra PostgreSQL real, las afirmaciones de la lección 07 sobre transacciones y SQLException, y el Clock de la 08. */
@SpringBootTest(classes = AplicacionTienda.class)
@Testcontainers
@Import(TransaccionesTest.Beans.class)
class TransaccionesTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @TestConfiguration
    static class Beans {
        @Bean Movimientos interno(JdbcTemplate jdbc) { return new Movimientos(jdbc, null); }
        @Bean Movimientos movimientos(JdbcTemplate jdbc, Movimientos interno) { return new Movimientos(jdbc, interno); }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired Movimientos movimientos;
    @Autowired Clock reloj;

    @BeforeEach
    void tablaVacia() {
        jdbc.execute("create table if not exists movimiento(id varchar(40) primary key)");
        jdbc.execute("truncate movimiento");
    }

    @Test
    void unaExcepcionComprobadaNoRevierteLaTransaccion() {
        assertThatThrownBy(() -> movimientos.insertarYLanzarComprobada("m1"))
                .isInstanceOf(Movimientos.ErrorComprobado.class);

        assertThat(existe("m1")).as("la fila quedó guardada: datos a medias").isTrue();
    }

    @Test
    void conRollbackForLaExcepcionComprobadaSiRevierte() {
        assertThatThrownBy(() -> movimientos.insertarYLanzarComprobadaConRollbackFor("m2"))
                .isInstanceOf(Movimientos.ErrorComprobado.class);

        assertThat(existe("m2")).isFalse();
    }

    @Test
    void unaExcepcionNoComprobadaRevierteLaTransaccion() {
        assertThatThrownBy(() -> movimientos.insertarYLanzarNoComprobada("m3"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(existe("m3")).isFalse();
    }

    @Test
    void capturarElFalloDeUnMetodoTransaccionalYSeguirTerminaEnUnexpectedRollbackException() {
        assertThatThrownBy(() -> movimientos.insertarYTragarseElFalloInterno("m4"))
                .isInstanceOf(UnexpectedRollbackException.class);

        assertThat(existe("m4")).isFalse();
    }

    @Test
    void springTraduceLaSQLExceptionALaJerarquiaDataAccessException() {
        jdbc.update("insert into movimiento(id) values ('m5')");

        assertThatThrownBy(() -> jdbc.update("insert into movimiento(id) values ('m5')"))
                .isInstanceOf(DuplicateKeyException.class)
                .isInstanceOf(DataAccessException.class)
                .hasRootCauseInstanceOf(java.sql.SQLException.class);
    }

    @Test
    void elRelojEsUnBeanInyectable() {
        assertThat(reloj.getZone()).isEqualTo(Clock.systemDefaultZone().getZone());
    }

    private boolean existe(String id) {
        return jdbc.queryForObject("select count(*) from movimiento where id = ?", Integer.class, id) == 1;
    }
}
