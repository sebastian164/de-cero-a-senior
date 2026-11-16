package soporte;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** Direcciones de la infraestructura de este taller (ver compose.yml) y utilidades de base de datos. */
public final class Entorno {

    public static final String PG_HOST = System.getProperty("pg.host", "localhost");
    public static final int PG_PUERTO = Integer.getInteger("pg.puerto", 43432);
    public static final String USUARIO = "curso";
    public static final String CLAVE = "curso";
    public static final String REDIS_HOST = "localhost";
    public static final int REDIS_PUERTO = 43379;
    public static final String MONGO_URI = "mongodb://localhost:43017";
    public static final String KAFKA = "localhost:43092";
    public static final String PG_PRIMARIO = "jdbc:postgresql://localhost:43433/curso";
    public static final String PG_REPLICA = "jdbc:postgresql://localhost:43434/curso";
    public static final String OTLP_HTTP = "http://localhost:43318";
    public static final String TEMPO = "http://localhost:43200";
    public static final String PROMETHEUS = "http://localhost:43090";

    private Entorno() {
    }

    public static String url(String base) {
        return "jdbc:postgresql://" + PG_HOST + ":" + PG_PUERTO + "/" + base;
    }

    /** Borra y crea una base de datos propia para el taller, y devuelve su URL JDBC. */
    public static String baseNueva(String nombre) {
        try (Connection c = DriverManager.getConnection(url("curso"), USUARIO, CLAVE);
             Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + nombre + " WITH (FORCE)");
            s.execute("CREATE DATABASE " + nombre);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo crear la base " + nombre, e);
        }
        return url(nombre);
    }

    public static Connection conectar(String url) throws SQLException {
        return DriverManager.getConnection(url, USUARIO, CLAVE);
    }

    /** Ejecuta un archivo SQL completo (puede tener varias sentencias y bloques DO). */
    public static void ejecutarArchivo(Connection c, Path archivo) throws SQLException, IOException {
        try (Statement s = c.createStatement()) {
            s.execute(Files.readString(archivo));
        }
    }
}
