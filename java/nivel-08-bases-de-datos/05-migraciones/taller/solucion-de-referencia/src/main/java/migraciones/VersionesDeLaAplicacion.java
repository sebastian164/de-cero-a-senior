package migraciones;

import org.springframework.jdbc.core.JdbcTemplate;

/** Las cuatro versiones de la aplicación que conviven durante el renombrado nombre → nombre_completo. */
public final class VersionesDeLaAplicacion {

    public interface Version {
        long crear(String nombre);
        String leer(long id);
    }

    private VersionesDeLaAplicacion() {
    }

    /** v1: solo conoce "nombre". */
    public static Version v1(JdbcTemplate jdbc) {
        return new Version() {
            public long crear(String n) {
                return jdbc.queryForObject("INSERT INTO clientes (nombre) VALUES (?) RETURNING id", Long.class, n);
            }
            public String leer(long id) {
                return jdbc.queryForObject("SELECT nombre FROM clientes WHERE id = ?", String.class, id);
            }
        };
    }

    /** v2: escribe en ambas, sigue leyendo "nombre". */
    public static Version v2(JdbcTemplate jdbc) {
        return new Version() {
            public long crear(String n) {
                return jdbc.queryForObject("INSERT INTO clientes (nombre, nombre_completo) VALUES (?, ?) RETURNING id", Long.class, n, n);
            }
            public String leer(long id) {
                return jdbc.queryForObject("SELECT nombre FROM clientes WHERE id = ?", String.class, id);
            }
        };
    }

    /** v3: escribe en ambas, lee "nombre_completo". */
    public static Version v3(JdbcTemplate jdbc) {
        return new Version() {
            public long crear(String n) {
                return jdbc.queryForObject("INSERT INTO clientes (nombre, nombre_completo) VALUES (?, ?) RETURNING id", Long.class, n, n);
            }
            public String leer(long id) {
                return jdbc.queryForObject("SELECT nombre_completo FROM clientes WHERE id = ?", String.class, id);
            }
        };
    }

    /** v4: solo "nombre_completo". */
    public static Version v4(JdbcTemplate jdbc) {
        return new Version() {
            public long crear(String n) {
                return jdbc.queryForObject("INSERT INTO clientes (nombre_completo) VALUES (?) RETURNING id", Long.class, n);
            }
            public String leer(long id) {
                return jdbc.queryForObject("SELECT nombre_completo FROM clientes WHERE id = ?", String.class, id);
            }
        };
    }
}
