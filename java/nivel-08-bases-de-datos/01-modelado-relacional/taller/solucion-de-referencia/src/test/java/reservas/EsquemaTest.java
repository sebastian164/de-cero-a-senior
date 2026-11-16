package reservas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import soporte.Entorno;

/** Comprueba en PostgreSQL real cada restricción del DDL del taller. */
class EsquemaTest {

    static Connection c;

    @BeforeAll
    static void crear() throws Exception {
        c = Entorno.conectar(Entorno.baseNueva("n8_01_esquema"));
        Entorno.ejecutarArchivo(c, Path.of("src/main/resources/esquema.sql"));
        sql("""
            INSERT INTO sedes (nombre, direccion) VALUES ('Norte', 'Calle 100'), ('Sur', 'Calle 1');
            INSERT INTO deportes (nombre) VALUES ('Fútbol 5'), ('Tenis');
            INSERT INTO canchas (sede_id, deporte_id, nombre) VALUES (1, 1, 'F5-A'), (1, 2, 'T-1'), (2, 1, 'F5-A');
            INSERT INTO tarifas (cancha_id, dia_semana, hora_inicio, hora_fin, precio_hora, vigente_desde)
                VALUES (1, 6, '18:00', '22:00', 120000, '2026-01-01');
            INSERT INTO clientes (documento, nombre, correo) VALUES ('100', 'Ana', 'ana@correo.co'), ('200', 'Luis', 'luis@correo.co');
            INSERT INTO reservas (cancha_id, sede_id, cliente_id, tarifa_id, inicio, fin, precio_total)
                VALUES (1, 1, 1, 1, '2026-11-21 18:00-05', '2026-11-21 19:00-05', 120000);
            """);
    }

    @AfterAll
    static void cerrar() throws SQLException {
        c.close();
    }

    static void sql(String s) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute(s);
        }
    }

    static String estadoSql(String s) {
        try {
            sql(s);
            return "OK";
        } catch (SQLException e) {
            return e.getSQLState();
        }
    }

    static String reserva(long cancha, long sede, String inicio, String fin) {
        return "INSERT INTO reservas (cancha_id, sede_id, cliente_id, tarifa_id, inicio, fin, precio_total) VALUES ("
                + cancha + "," + sede + ",2,1,'" + inicio + "','" + fin + "',120000)";
    }

    @Test
    void dos_reservas_de_la_misma_cancha_que_se_solapan_se_rechazan() {
        assertThat(estadoSql(reserva(1, 1, "2026-11-21 18:30-05", "2026-11-21 19:30-05"))).isEqualTo("23P01");
        // la misma franja expresada en otra zona horaria también choca: se compara el instante, no el texto
        assertThat(estadoSql(reserva(1, 1, "2026-11-21 23:00+00", "2026-11-22 00:00+00"))).isEqualTo("23P01");
    }

    @Test
    void franjas_contiguas_y_otras_canchas_si_se_permiten() {
        assertThat(estadoSql(reserva(1, 1, "2026-11-21 19:00-05", "2026-11-21 20:00-05"))).isEqualTo("OK");
        assertThat(estadoSql(reserva(2, 1, "2026-11-21 18:00-05", "2026-11-21 19:00-05"))).isEqualTo("OK");
    }

    @Test
    void una_reserva_cancelada_libera_la_franja() throws SQLException {
        sql(reserva(1, 1, "2026-11-28 18:00-05", "2026-11-28 19:00-05"));
        assertThat(estadoSql(reserva(1, 1, "2026-11-28 18:00-05", "2026-11-28 19:00-05"))).isEqualTo("23P01");
        sql("UPDATE reservas SET estado = 'CANCELADA' WHERE inicio = '2026-11-28 18:00-05'");
        assertThat(estadoSql(reserva(1, 1, "2026-11-28 18:00-05", "2026-11-28 19:00-05"))).isEqualTo("OK");
    }

    @Test
    void la_sede_copiada_no_puede_contradecir_a_la_cancha() {
        assertThat(estadoSql(reserva(1, 2, "2026-12-05 18:00-05", "2026-12-05 19:00-05"))).isEqualTo("23503");
    }

    @Test
    void restricciones_de_dominio() {
        assertThat(estadoSql(reserva(3, 2, "2026-12-05 19:00-05", "2026-12-05 18:00-05"))).isEqualTo("23514");
        assertThat(estadoSql("UPDATE reservas SET estado = 'BORRADA' WHERE id = 1")).isEqualTo("23514");
        assertThat(estadoSql("INSERT INTO clientes (documento, nombre, correo) VALUES ('300','Eva','ANA@correo.co')"))
                .isEqualTo("23505");
        assertThat(estadoSql("INSERT INTO canchas (sede_id, deporte_id, nombre) VALUES (1, 1, 'F5-A')")).isEqualTo("23505");
        assertThat(estadoSql("INSERT INTO productos_inexistentes VALUES (1)")).isEqualTo("42P01");
    }

    @Test
    void tarifas_que_se_pisan_en_el_mismo_horario_y_vigencia_se_rechazan() {
        assertThat(estadoSql("""
            INSERT INTO tarifas (cancha_id, dia_semana, hora_inicio, hora_fin, precio_hora, vigente_desde)
            VALUES (1, 6, '21:00', '23:00', 150000, '2026-06-01')""")).isEqualTo("23P01");
        // misma franja, pero vigencia que empieza cuando la anterior termina: válida
        assertThat(estadoSql("UPDATE tarifas SET vigente_hasta = '2027-01-01' WHERE id = 1")).isEqualTo("OK");
        assertThat(estadoSql("""
            INSERT INTO tarifas (cancha_id, dia_semana, hora_inicio, hora_fin, precio_hora, vigente_desde)
            VALUES (1, 6, '18:00', '22:00', 150000, '2027-01-01')""")).isEqualTo("OK");
    }

    @Test
    void pagos_y_cancelaciones_no_se_duplican() throws SQLException {
        sql("INSERT INTO pagos (reserva_id, monto, metodo, estado, referencia) VALUES (1, 120000, 'PSE', 'APROBADO', 'PSE-1')");
        assertThat(estadoSql("INSERT INTO pagos (reserva_id, monto, metodo, estado, referencia) VALUES (1, 120000, 'PSE', 'APROBADO', 'PSE-1')"))
                .isEqualTo("23505");
        sql(reserva(3, 2, "2026-12-12 18:00-05", "2026-12-12 19:00-05"));
        String cancelar = "INSERT INTO cancelaciones (reserva_id, motivo, cancelada_por, porcentaje_reembolso, monto_reembolso) "
                + "SELECT id, 'lluvia', 'SEDE', 100, 120000 FROM reservas WHERE cancha_id = 3";
        assertThat(estadoSql(cancelar)).isEqualTo("OK");
        assertThat(estadoSql(cancelar)).isEqualTo("23505");
    }

    @Test
    void el_precio_copiado_conserva_la_historia_cuando_cambia_la_tarifa() throws SQLException {
        sql("UPDATE tarifas SET precio_hora = 999999 WHERE id = 1");
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT precio_total FROM reservas WHERE id = 1")) {
            rs.next();
            assertThat(rs.getBigDecimal(1)).isEqualByComparingTo("120000");
        }
        sql("UPDATE tarifas SET precio_hora = 120000 WHERE id = 1");
    }

    @Test
    void la_clave_foranea_impide_borrar_una_cancha_con_reservas() {
        assertThatThrownBy(() -> sql("DELETE FROM canchas WHERE id = 1"))
                .isInstanceOf(SQLException.class)
                .extracting(e -> ((SQLException) e).getSQLState()).isEqualTo("23503");
    }
}
