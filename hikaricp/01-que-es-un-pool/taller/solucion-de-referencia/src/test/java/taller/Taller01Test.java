package taller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/** Los nueve pasos del taller 01 contra PostgreSQL real (max_connections=30). Los números van a resultados/resultados.md. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Taller01Test {

    static final String URL = "jdbc:postgresql://localhost:" + System.getProperty("db.puerto", "5432") + "/tienda";
    static final String CLAVE = System.getProperty("db.clave", "local");
    static final Pasos PASOS = new Pasos(URL, "postgres", CLAVE);
    static final Path RESULTADOS = Path.of("resultados/resultados.md");
    static final int VECES = 1_000;

    @BeforeAll
    static void empezar() throws IOException {
        Files.createDirectories(RESULTADOS.getParent());
        Files.writeString(RESULTADOS, "# Resultados del taller 01\n\n");
    }

    @Test
    @Order(1)
    void pasos1a4_tiemposYConexionesVistasPorPostgres() throws Exception {
        Muestreo muestreo = new Muestreo();
        Pasos.Medicion abriendo = muestreo.durante(() -> PASOS.abriendoCadaVez(VECES));
        long maximoAbriendo = muestreo.maximo();
        Pasos.Medicion una = PASOS.unaSolaConexion(VECES);
        Pasos.Medicion hikari;
        long maximoHikari;
        try (HikariDataSource pool = PASOS.pool(10)) {
            Thread.sleep(1_500);                                  // el pool abre en segundo plano hasta minimumIdle (10)
            Muestreo otro = new Muestreo();
            hikari = otro.durante(() -> PASOS.conHikari(pool, VECES));
            maximoHikari = otro.maximo();
        }
        anotar("## Pasos 1 a 3: mil `select 1`\n\n| Variante | Total | Promedio por operación |\n|---|---|---|\n");
        for (Pasos.Medicion m : List.of(abriendo, una, hikari)) {
            anotar("| %s | %d ms | %.3f ms |\n".formatted(m.nombre(), m.totalMs(), m.promedioMs()));
        }
        anotar("\n## Paso 4: conexiones en pg_stat_activity durante la prueba\n\n"
                + "- Abriendo cada vez: como mucho %d a la vez (cada una vive unos milisegundos)\n".formatted(maximoAbriendo)
                + "- Con HikariCP (pool de 10): %d, abiertas todo el tiempo\n\n".formatted(maximoHikari));
        assertTrue(abriendo.totalMs() > 5 * hikari.totalMs(), "abrir cada vez es mucho más lento que el pool");
        assertTrue(maximoAbriendo <= 2, "sin pool, cada conexión vive solo lo que dura su consulta");
        assertEquals(10, maximoHikari, "el pool mantiene sus diez conexiones abiertas");
    }

    @Test
    @Order(2)
    void paso5_cincuentaHilosContraTreintaConexiones() throws Exception {
        Pasos.Concurrencia sinPool = PASOS.hilosSinPool(50, 40);
        Pasos.Concurrencia conPool;
        try (HikariDataSource pool = PASOS.pool(10)) {
            conPool = PASOS.hilosConPool(pool, 50, 40);
        }
        anotar("## Paso 5: 50 hilos x 40 consultas\n\nSin pool: %d correctas en %d ms. Errores:\n\n".formatted(sinPool.correctas(), sinPool.totalMs()));
        sinPool.errores().forEach((mensaje, veces) -> anotar("    %dx %s\n".formatted(veces, mensaje)));
        anotar("\nCon un pool de 10: %d correctas, %d errores, en %d ms. Los hilos sin conexión esperan su turno.\n\n"
                .formatted(conPool.correctas(), conPool.errores().size(), conPool.totalMs()));
        assertTrue(sinPool.errores().keySet().stream().anyMatch(e -> e.contains("FATAL: sorry, too many clients already")),
                "sin pool, PostgreSQL rechaza las que pasan de su límite: " + sinPool.errores().keySet());
        assertEquals(2_000, conPool.correctas(), "con pool, todas terminan");
        assertTrue(conPool.errores().isEmpty());
    }

    @Test
    @Order(3)
    void paso6_laUndecimaConexionSinDevolver() throws Exception {
        List<Connection> retenidas = new ArrayList<>();
        try (HikariDataSource pool = PASOS.pool(10)) {
            for (int i = 0; i < 10; i++) {
                retenidas.add(pool.getConnection());              // sin close(), a propósito
            }
            long inicio = System.nanoTime();
            SQLException error = assertThrows(SQLTransientConnectionException.class, pool::getConnection);
            long ms = Pasos.ms(inicio);
            anotar("## Paso 6: la undécima conexión\n\nTardó %d ms en fallar (connectionTimeout por defecto: %d ms).\n\n    %s: %s\n\n"
                    .formatted(ms, pool.getConnectionTimeout(), error.getClass().getName(), error.getMessage()));
            assertTrue(ms >= 29_900 && ms < 32_000, "falla al cumplirse connectionTimeout, 30 s: " + ms);
            assertTrue(error.getMessage().matches(
                    "HikariPool-\\d+ - Connection is not available, request timed out after \\d+ms \\(total=10, active=10, idle=0, waiting=0\\)"),
                    error.getMessage());
            for (Connection c : retenidas) {
                c.close();
            }
        }
    }

    @Test
    @Order(4)
    void paso7_registroEnDepuracion() throws Exception {
        String nombre;
        try (HikariDataSource pool = PASOS.pool(10); Connection conexion = pool.getConnection()) {
            nombre = pool.getPoolName();
            Pasos.seleccionarUno(conexion);
            Thread.sleep(2_500);                                  // con housekeeping.periodMs=1000 (ver pom), dos vueltas del vigilante
        }
        String registro = String.join("\n", Files.readString(Path.of("target/hikari.log")).lines()
                .filter(l -> l.contains(nombre + " ") || l.contains(nombre + ":") || l.contains("HikariConfig - ")).toList());
        anotar("## Paso 7: líneas del registro en DEBUG (extracto)\n\n");
        registro.lines().filter(l -> l.contains("configuration:") || l.contains("maximumPoolSize")
                        || l.contains("Added connection") || l.contains("Pool stats") || l.contains("Fill pool skipped")
                        || l.contains("Start completed") || l.contains("Shutdown"))
                .limit(18).forEach(l -> anotar("    " + l.replaceAll("^\\d+ ", "") + "\n"));
        anotar("\n");
        assertTrue(registro.contains("configuration:"), "al arrancar vuelca la configuración completa");
        assertTrue(registro.contains("Added connection"), "abre conexiones hasta minimumIdle");
        assertTrue(registro.matches("(?s).*Pool stats \\(total=\\d+/10, idle=\\d+/10, active=\\d+, waiting=0\\).*"), "cada vuelta, el estado del pool");
        assertTrue(registro.contains("Fill pool skipped, pool has sufficient level"), "cada vuelta, comprueba si hay que rellenar");
    }

    @Test
    @Order(5)
    void paso8_dosPoolsPorError() throws Exception {
        try (HikariDataSource uno = PASOS.pool(10); HikariDataSource otro = PASOS.pool(10)) {
            uno.getConnection().close();
            otro.getConnection().close();
            Thread.sleep(1_500);
            long vistas = conexionesDeLaApp();
            anotar("## Paso 8: dos HikariDataSource a la misma base\n\nPostgreSQL ve %d conexiones.\n\n".formatted(vistas));
            assertEquals(20, vistas, "cada pool abre las suyas: el doble");
        }
    }

    @Test
    @Order(6)
    void paso9_cerrarONoCerrarElPool() throws Exception {
        HikariDataSource abandonado = PASOS.pool(10);
        abandonado.getConnection().close();
        Thread.sleep(1_500);
        long sinCerrar = conexionesDeLaApp();
        abandonado.close();
        Thread.sleep(300);
        long cerrado = conexionesDeLaApp();

        Process hijo = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), "taller.SinCerrar", URL, "postgres", CLAVE)
                .redirectErrorStream(true).start();
        String salida = new String(hijo.getInputStream().readAllBytes());
        hijo.waitFor();
        Thread.sleep(500);
        long trasMorir = conexionesDeLaApp();
        anotar("## Paso 9: cerrar el pool\n\n- Pool sin cerrar, proceso vivo: %d conexiones en la base\n".formatted(sinCerrar)
                + "- Tras `close()`: %d\n".formatted(cerrado)
                + "- Un proceso que termina sin cerrar el pool (%s): %d al morir el proceso\n".formatted(salida.strip(), trasMorir));
        assertEquals(10, sinCerrar, "mientras el proceso vive, un pool sin cerrar mantiene sus conexiones");
        assertEquals(0, cerrado);
        assertEquals(0, trasMorir, "si el proceso muere, el sistema operativo cierra los sockets y PostgreSQL lo nota");
        assertFalse(salida.isBlank());
    }

    // ---- utilidades ----

    static long conexionesDeLaApp() throws SQLException {
        try (Connection admin = DriverManager.getConnection(URL, "postgres", CLAVE); Statement s = admin.createStatement();
             ResultSet fila = s.executeQuery("select count(*) from pg_stat_activity where datname = 'tienda' "
                     + "and backend_type = 'client backend' and pid <> pg_backend_pid()")) {
            fila.next();
            return fila.getLong(1);
        }
    }

    static void anotar(String texto) {
        try {
            Files.writeString(RESULTADOS, texto, StandardOpenOption.APPEND);
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }

    interface Medible {
        Pasos.Medicion medir() throws Exception;
    }

    /** Pregunta a PostgreSQL cada 5 ms cuántas conexiones de la aplicación hay, mientras corre un experimento. */
    static final class Muestreo {
        private final AtomicLong maximo = new AtomicLong();

        Pasos.Medicion durante(Medible experimento) throws Exception {
            AtomicBoolean seguir = new AtomicBoolean(true);
            Thread hilo = Thread.ofPlatform().start(() -> {
                try (Connection admin = DriverManager.getConnection(URL, "postgres", CLAVE); Statement s = admin.createStatement()) {
                    while (seguir.get()) {
                        try (ResultSet fila = s.executeQuery("select count(*) from pg_stat_activity where datname = 'tienda' "
                                + "and backend_type = 'client backend' and pid <> pg_backend_pid()")) {
                            fila.next();
                            maximo.accumulateAndGet(fila.getLong(1), Math::max);
                        }
                        Thread.sleep(5);
                    }
                } catch (Exception error) {
                    throw new IllegalStateException(error);
                }
            });
            try {
                return experimento.medir();
            } finally {
                seguir.set(false);
                hilo.join();
            }
        }

        long maximo() {
            return maximo.get();
        }
    }
}
