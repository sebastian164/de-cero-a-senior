package transacciones;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import soporte.Entorno;

/**
 * Pasos 1 y 2 del taller: dos sesiones intercaladas a mano, igual que dos ventanas de psql.
 * Cada prueba indica en qué nivel aparece la anomalía y en cuál desaparece en PostgreSQL.
 */
class AnomaliasTest {

    static String url;
    Sesion t1;
    Sesion t2;

    @BeforeAll
    static void base() {
        url = Entorno.baseNueva("n8_03_anomalias");
    }

    @BeforeEach
    void preparar() throws Exception {
        t1 = new Sesion(url);
        t2 = new Sesion(url);
        t1.sql("""
            DROP TABLE IF EXISTS productos, medicos;
            CREATE TABLE productos (id INT PRIMARY KEY, nombre TEXT, stock INT NOT NULL, precio INT NOT NULL, version INT NOT NULL DEFAULT 0);
            INSERT INTO productos VALUES (7, 'balón', 10, 100, 5), (8, 'red', 3, 50, 0);
            CREATE TABLE medicos (nombre TEXT PRIMARY KEY, de_guardia BOOLEAN NOT NULL);
            INSERT INTO medicos VALUES ('Ana', true), ('Beto', true);
            """);
    }

    @AfterEach
    void cerrar() throws Exception {
        t1.close();
        t2.close();
    }

    @ParameterizedTest(name = "lectura no repetible en {0}: aparece = {1}")
    @CsvSource({"READ COMMITTED, true", "REPEATABLE READ, false", "SERIALIZABLE, false"})
    void lectura_no_repetible(String nivel, boolean aparece) throws Exception {
        t1.sql("BEGIN ISOLATION LEVEL " + nivel);
        int primera = t1.entero("SELECT precio FROM productos WHERE id = 7");
        t2.sql("UPDATE productos SET precio = 120 WHERE id = 7");          // T2 confirma (autocommit)
        int segunda = t1.entero("SELECT precio FROM productos WHERE id = 7");
        t1.sql("COMMIT");
        assertThat(primera != segunda).isEqualTo(aparece);
    }

    @ParameterizedTest(name = "lectura fantasma en {0}: aparece = {1}")
    @CsvSource({"READ COMMITTED, true", "REPEATABLE READ, false", "SERIALIZABLE, false"})
    void lectura_fantasma(String nivel, boolean aparece) throws Exception {
        t1.sql("BEGIN ISOLATION LEVEL " + nivel);
        int antes = t1.entero("SELECT count(*) FROM productos WHERE precio < 200");
        t2.sql("INSERT INTO productos VALUES (9, 'guantes', 5, 80)");
        int despues = t1.entero("SELECT count(*) FROM productos WHERE precio < 200");
        t1.sql("COMMIT");
        // En PostgreSQL, REPEATABLE READ ya evita los fantasmas (la tabla del README dice "según el motor").
        assertThat(antes != despues).isEqualTo(aparece);
    }

    @Test
    void actualizacion_perdida_en_read_committed() throws Exception {
        t1.sql("BEGIN");
        t2.sql("BEGIN");
        int leido1 = t1.entero("SELECT stock FROM productos WHERE id = 7");    // 10
        int leido2 = t2.entero("SELECT stock FROM productos WHERE id = 7");    // 10
        t1.sql("UPDATE productos SET stock = " + (leido1 - 1) + " WHERE id = 7");
        t1.sql("COMMIT");
        t2.sql("UPDATE productos SET stock = " + (leido2 - 1) + " WHERE id = 7");
        t2.sql("COMMIT");
        assertThat(t1.entero("SELECT stock FROM productos WHERE id = 7")).isEqualTo(9);   // se vendieron 2, se descontó 1
    }

    @Test
    void en_repeatable_read_la_actualizacion_perdida_se_detecta() throws Exception {
        t1.sql("BEGIN ISOLATION LEVEL REPEATABLE READ");
        t2.sql("BEGIN ISOLATION LEVEL REPEATABLE READ");
        int leido1 = t1.entero("SELECT stock FROM productos WHERE id = 7");
        int leido2 = t2.entero("SELECT stock FROM productos WHERE id = 7");
        t1.sql("UPDATE productos SET stock = " + (leido1 - 1) + " WHERE id = 7");
        t1.sql("COMMIT");
        // 40001: could not serialize access due to concurrent update. La aplicación debe reintentar.
        assertThat(t2.intentar("UPDATE productos SET stock = " + (leido2 - 1) + " WHERE id = 7")).isEqualTo("40001");
        t2.sql("ROLLBACK");
    }

    @ParameterizedTest(name = "sesgo de escritura en {0}: ambos salen = {1}")
    @CsvSource({"READ COMMITTED, true", "REPEATABLE READ, true", "SERIALIZABLE, false"})
    void sesgo_de_escritura_medicos_de_guardia(String nivel, boolean ambosSalen) throws Exception {
        t1.sql("BEGIN ISOLATION LEVEL " + nivel);
        t2.sql("BEGIN ISOLATION LEVEL " + nivel);
        // Cada uno comprueba la regla "debe quedar al menos uno" y ve dos de guardia.
        assertThat(t1.entero("SELECT count(*) FROM medicos WHERE de_guardia")).isEqualTo(2);
        assertThat(t2.entero("SELECT count(*) FROM medicos WHERE de_guardia")).isEqualTo(2);
        t1.sql("UPDATE medicos SET de_guardia = false WHERE nombre = 'Ana'");
        String u2 = t2.intentar("UPDATE medicos SET de_guardia = false WHERE nombre = 'Beto'");   // filas distintas: no hay bloqueo
        String r1 = t1.intentar("COMMIT");
        String r2 = u2.equals("OK") ? t2.intentar("COMMIT") : u2;
        t2.intentar("ROLLBACK");
        int quedan = t1.entero("SELECT count(*) FROM medicos WHERE de_guardia");
        if (ambosSalen) {
            assertThat(r1).isEqualTo("OK");
            assertThat(r2).isEqualTo("OK");
            assertThat(quedan).isZero();                      // la regla se rompió
        } else {
            assertThat(r1).isEqualTo("OK");
            assertThat(r2).isEqualTo("40001");                // SERIALIZABLE aborta al segundo
            assertThat(quedan).isEqualTo(1);
        }
    }
}
