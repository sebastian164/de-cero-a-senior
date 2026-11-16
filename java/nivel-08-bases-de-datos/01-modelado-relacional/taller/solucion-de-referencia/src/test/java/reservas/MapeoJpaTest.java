package reservas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import reservas.jpa.AplicacionReservas;
import reservas.jpa.Cancha;
import reservas.jpa.EstadoReserva;
import reservas.jpa.Reserva;
import soporte.Entorno;

/** Las entidades se validan contra el DDL real (ddl-auto=validate) y se comprueba qué reglas solo conoce la base. */
@SpringBootTest(classes = AplicacionReservas.class)
class MapeoJpaTest {

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry r) throws Exception {
        String url = Entorno.baseNueva("n8_01_jpa");
        try (Connection c = Entorno.conectar(url)) {
            Entorno.ejecutarArchivo(c, Path.of("src/main/resources/esquema.sql"));
            c.createStatement().execute("""
                INSERT INTO sedes (nombre, direccion) VALUES ('Norte', 'Calle 100');
                INSERT INTO deportes (nombre) VALUES ('Fútbol 5');
                INSERT INTO clientes (documento, nombre, correo) VALUES ('100', 'Ana', 'ana@correo.co');
                """);
        }
        r.add("spring.datasource.url", () -> url);
        r.add("spring.datasource.username", () -> Entorno.USUARIO);
        r.add("spring.datasource.password", () -> Entorno.CLAVE);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;

    @Test
    void el_mapeo_coincide_con_el_esquema_y_la_base_impide_el_solapamiento() {
        Long canchaId = tx.execute(s -> {
            Cancha cancha = new Cancha(1L, 1L, "F5-A");
            em.persist(cancha);
            em.flush();
            em.createNativeQuery("INSERT INTO tarifas (cancha_id, dia_semana, hora_inicio, hora_fin, precio_hora, vigente_desde) "
                    + "VALUES (" + cancha.getId() + ", 6, '18:00', '22:00', 120000, '2026-01-01')").executeUpdate();
            return cancha.getId();
        });
        Instant inicio = Instant.parse("2026-11-21T23:00:00Z");
        Long reservaId = tx.execute(s -> {
            Reserva r = new Reserva(em.find(Cancha.class, canchaId), 1L, 1L, inicio, inicio.plusSeconds(3600), new BigDecimal("120000.00"));
            em.persist(r);
            return r.getId();
        });
        assertThat(reservaId).isNotNull();

        // el objeto no sabe nada de la regla; la base la hace cumplir al confirmar
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            Reserva otra = new Reserva(em.find(Cancha.class, canchaId), 1L, 1L, inicio.plusSeconds(1800), inicio.plusSeconds(5400), BigDecimal.TEN);
            em.persist(otra);
            em.flush();
        })).isInstanceOf(org.hibernate.exception.ConstraintViolationException.class)   // sin @Repository no hay traducción a DataIntegrityViolationException
        .hasMessageContaining("reservas_sin_solapamiento");

        tx.executeWithoutResult(s -> em.find(Reserva.class, reservaId).cancelar());
        Long nueva = tx.execute(s -> {
            Reserva r = new Reserva(em.find(Cancha.class, canchaId), 1L, 1L, inicio, inicio.plusSeconds(3600), new BigDecimal("120000.00"));
            em.persist(r);
            return r.getId();
        });
        EstadoReserva estado = tx.execute(s -> em.find(Reserva.class, nueva).getEstado());
        assertThat(estado).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
    }
}
