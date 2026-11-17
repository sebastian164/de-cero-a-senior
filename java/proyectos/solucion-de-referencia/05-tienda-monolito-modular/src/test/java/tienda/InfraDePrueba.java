package tienda;

import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * PostgreSQL 18.7 y Kafka 4.3.1 reales, en contenedores de Testcontainers que se levantan una sola vez por ejecución.
 * No necesitas docker compose para probar: basta Docker en marcha.
 */
final class InfraDePrueba {
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.7")
            .withDatabaseName("postgres").withUsername("tienda").withPassword("tienda");
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.1");

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    private InfraDePrueba() { }

    /** Crea una base de datos vacía y devuelve su URL JDBC. */
    static String baseNueva(String nombre) {
        try (Connection c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "tienda", "tienda"); Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + nombre + " WITH (FORCE)");
            s.execute("CREATE DATABASE " + nombre);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo crear la base " + nombre, e);
        }
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + nombre;
    }

    static int puertoLibre() {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
