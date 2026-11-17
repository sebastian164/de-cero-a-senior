package reservas;

import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Un PostgreSQL real en un contenedor de Testcontainers, compartido por todas las pruebas de la ejecución (se levanta una vez).
 * Cada clase de prueba pide su propia base de datos vacía, así que no se pisan entre sí. No necesitas docker compose para probar.
 */
final class PostgresDePrueba {
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.7")
            .withDatabaseName("postgres").withUsername("reservas").withPassword("reservas");

    static {
        POSTGRES.start();
    }

    private PostgresDePrueba() { }

    /** Crea una base de datos vacía con ese nombre y devuelve su URL JDBC. */
    static String baseNueva(String nombre) {
        try (Connection c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "reservas", "reservas");
             Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + nombre + " WITH (FORCE)");
            s.execute("CREATE DATABASE " + nombre);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo crear la base " + nombre, e);
        }
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + nombre;
    }

    /** Un puerto libre para arrancar la aplicación sin chocar con nada del equipo. */
    static int puertoLibre() {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
