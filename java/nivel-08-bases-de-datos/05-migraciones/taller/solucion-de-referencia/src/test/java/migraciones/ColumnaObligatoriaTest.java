package migraciones;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import soporte.Entorno;

/**
 * Paso 4: columna obligatoria en una tabla de un millón de filas, mientras otro hilo inserta sin parar.
 * Se mide cuánto tarda la DDL y la peor espera que sufrió el escritor.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ColumnaObligatoriaTest {

    static String url;
    static Connection ddl;
    static final StringBuilder informe = new StringBuilder("| Estrategia | Duración de la DDL | Peor espera del escritor |\n|---|---:|---:|\n");

    @BeforeAll
    static void cargar() throws Exception {
        url = Entorno.baseNueva("n8_05_millon");
        ddl = Entorno.conectar(url);
        sql("""
            CREATE TABLE pedidos (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, total NUMERIC(12,2) NOT NULL,
                                  creado_en TIMESTAMPTZ NOT NULL DEFAULT now());
            INSERT INTO pedidos (total) SELECT (g % 1000) + 0.5 FROM generate_series(1, 1000000) g;
            ALTER TABLE pedidos SET (autovacuum_enabled = false);   -- que el autovacuum no interfiera con las medidas
            """);
        sql("VACUUM ANALYZE pedidos");
        sql("SET lock_timeout = '30s'");
    }

    @AfterAll
    static void cerrar() throws Exception {
        Files.writeString(Path.of("target/columna-obligatoria.md"), informe);
        System.out.println(informe);
        ddl.close();
    }

    static void sql(String s) throws SQLException {
        try (Statement st = ddl.createStatement()) {
            st.execute(s);
        }
    }

    record Medicion(double ddlMs, double peorEsperaMs) {}

    interface Ddl {
        void ejecutar() throws Exception;
    }

    /** Ejecuta la DDL con un escritor concurrente que inserta un pedido tras otro. */
    static Medicion conEscritor(String nombre, Ddl cambio) throws Exception {
        return conEscritor(nombre, "INSERT INTO pedidos (total) VALUES (1)", cambio);
    }

    static Medicion conEscritor(String nombre, String insercion, Ddl cambio) throws Exception {
        AtomicBoolean seguir = new AtomicBoolean(true);
        AtomicLong peor = new AtomicLong();
        Thread escritor = new Thread(() -> {
            try (Connection c = Entorno.conectar(url);
                 PreparedStatement ps = c.prepareStatement(insercion)) {
                while (seguir.get()) {
                    long t0 = System.nanoTime();
                    ps.executeUpdate();
                    peor.accumulateAndGet(System.nanoTime() - t0, Math::max);
                }
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        });
        escritor.start();
        Thread.sleep(300);
        peor.set(0);
        long t0 = System.nanoTime();
        cambio.ejecutar();
        double ms = (System.nanoTime() - t0) / 1e6;
        Thread.sleep(300);
        seguir.set(false);
        escritor.join();
        Medicion m = new Medicion(ms, peor.get() / 1e6);
        informe.append(String.format(Locale.ROOT, "| %s | %.0f ms | %.0f ms |%n", nombre, m.ddlMs(), m.peorEsperaMs()));
        return m;
    }

    @Test
    @Order(1)
    void not_null_sin_valor_por_defecto_falla() {
        try {
            sql("ALTER TABLE pedidos ADD COLUMN canal VARCHAR(20) NOT NULL");
            throw new AssertionError("debió fallar");
        } catch (SQLException e) {
            assertThat(e.getSQLState()).isEqualTo("23502");   // not_null_violation: las filas existentes no tienen valor
            informe.append("| `ADD COLUMN … NOT NULL` sin valor por defecto | falla: ").append(e.getMessage().lines().findFirst().orElse(""))
                    .append(" | — |\n");
        }
    }

    static Medicion constante;
    static Medicion volatil;

    @Test
    @Order(2)
    void not_null_con_default_constante_es_instantaneo() throws Exception {
        constante = conEscritor("`ADD COLUMN canal … NOT NULL DEFAULT 'WEB'` (constante)",
                () -> sql("ALTER TABLE pedidos ADD COLUMN canal VARCHAR(20) NOT NULL DEFAULT 'WEB'"));
        assertThat(constante.ddlMs()).isLessThan(500);
    }

    @Test
    @Order(3)
    void not_null_con_default_volatil_reescribe_la_tabla_y_bloquea() throws Exception {
        volatil = conEscritor("`ADD COLUMN token UUID NOT NULL DEFAULT gen_random_uuid()` (volátil)",
                () -> sql("ALTER TABLE pedidos ADD COLUMN token UUID NOT NULL DEFAULT gen_random_uuid()"));
        assertThat(volatil.peorEsperaMs()).isGreaterThan(5 * constante.peorEsperaMs());
        sql("ALTER TABLE pedidos DROP COLUMN token");
    }

    /** Desde el paso 2 corre la versión nueva de la aplicación, que ya escribe la región. */
    static final String NUEVA = "INSERT INTO pedidos (total, region) VALUES (1, 'ANDINA')";

    @Test
    @Order(4)
    void forma_segura_nula_relleno_por_lotes_check_not_valid_y_set_not_null() throws Exception {
        Medicion agregar = conEscritor("Segura 1: `ADD COLUMN region VARCHAR(20)` (nula)",
                () -> sql("ALTER TABLE pedidos ADD COLUMN region VARCHAR(20)"));
        Medicion relleno = conEscritor("Segura 2: relleno por lotes de 50.000 (transacciones cortas)", NUEVA, () -> {
            long maximo;
            try (Statement st = ddl.createStatement(); var rs = st.executeQuery("SELECT max(id) FROM pedidos")) {
                rs.next();
                maximo = rs.getLong(1) + 100_000;    // margen para lo que inserta el escritor mientras tanto
            }
            for (long desde = 1; desde <= maximo; desde += 50_000) {
                try (Statement st = ddl.createStatement()) {     // autocommit: cada lote es una transacción corta
                    st.executeUpdate("UPDATE pedidos SET region = CASE WHEN id % 2 = 0 THEN 'ANDINA' ELSE 'CARIBE' END "
                            + "WHERE id BETWEEN " + desde + " AND " + (desde + 49_999) + " AND region IS NULL");
                }
            }
        });
        Medicion check = conEscritor("Segura 3: `ADD CONSTRAINT … CHECK (region IS NOT NULL) NOT VALID`", NUEVA,
                () -> sql("ALTER TABLE pedidos ADD CONSTRAINT region_obligatoria CHECK (region IS NOT NULL) NOT VALID"));
        Medicion validar = conEscritor("Segura 4: `VALIDATE CONSTRAINT` (no bloquea escrituras)", NUEVA,
                () -> sql("ALTER TABLE pedidos VALIDATE CONSTRAINT region_obligatoria"));
        Medicion notNull = conEscritor("Segura 5: `SET NOT NULL` (usa el CHECK validado, no recorre la tabla)", NUEVA,
                () -> sql("ALTER TABLE pedidos ALTER COLUMN region SET NOT NULL; ALTER TABLE pedidos DROP CONSTRAINT region_obligatoria"));
        for (Medicion m : new Medicion[] {agregar, relleno, check, validar, notNull}) {
            assertThat(m.peorEsperaMs()).isLessThan(volatil.peorEsperaMs());
        }
        assertThat(notNull.ddlMs()).isLessThan(500);
    }
}
