package tienda.adaptadores.salida.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tienda.ContratoDelInventario;
import tienda.ContratoDelRepositorioDePedidos;

/** Pasos 4 y 9 del taller 06, con PostgreSQL real: los contratos del servicio de pedidos y el bloqueo de la unidad anidada. */
class ConPostgresTest {

    private static HikariDataSource pool;

    static HikariDataSource nuevoPool(int tamano, long esperaMs) {
        HikariConfig configuracion = new HikariConfig();
        configuracion.setJdbcUrl(System.getenv("DB_URL"));
        configuracion.setUsername(System.getenv("DB_USUARIO"));
        configuracion.setPassword(System.getenv("DB_PASSWORD"));
        configuracion.setMaximumPoolSize(tamano);
        configuracion.setConnectionTimeout(esperaMs);
        return new HikariDataSource(configuracion);
    }

    @BeforeAll
    static void crearTablas() throws Exception {
        pool = nuevoPool(4, 2_000);
        // El DDL de la tienda: el del módulo hexagonal (sesión 07), el mismo que usa el servicio con JPA.
        String ddl = Files.readString(Path.of(System.getProperty("schema")));
        try (Connection conexion = pool.getConnection(); Statement sentencia = conexion.createStatement()) {
            sentencia.execute(ddl);
        }
    }

    @AfterAll
    static void cerrar() {
        pool.close();
    }

    @Test
    void elContratoDelRepositorioDePedidosLoCumpleElRepositorioJdbc() {
        UnidadDeTrabajoJdbc transacciones = new UnidadDeTrabajoJdbc(pool);
        List<String> incumplidas = ContratoDelRepositorioDePedidos.incumplimientos(
                () -> new ContratoDelRepositorioDePedidos.Escenario(new PedidosJdbc(transacciones), transacciones));
        assertEquals(List.of(), incumplidas);
    }

    @Test
    void elContratoDelInventarioLoCumpleElInventarioJdbc() throws Exception {
        String producto = "contrato-" + UUID.randomUUID();
        try (Connection conexion = pool.getConnection(); PreparedStatement sentencia = conexion.prepareStatement(
                "insert into producto (id, precio_centavos) values (?, 100)")) {
            sentencia.setString(1, producto);                    // la clave foránea de existencias lo exige
            sentencia.executeUpdate();
        }
        UnidadDeTrabajoJdbc transacciones = new UnidadDeTrabajoJdbc(pool);
        List<String> incumplidas = ContratoDelInventario.incumplimientos(
                () -> new ContratoDelInventario.Escenario(new InventarioJdbc(transacciones), transacciones, producto));
        assertEquals(List.of(), incumplidas);
    }

    @Test
    void conElPoolEnUnoUnaUnidadAnidadaQueSePideOtraConexionSeBloquea() {
        try (HikariDataSource uno = nuevoPool(1, 500)) {
            UnidadDeTrabajoQueSiemprePideOtra defectuosa = new UnidadDeTrabajoQueSiemprePideOtra(uno);   // el defecto: la anidada pide otra
            long inicio = System.nanoTime();
            IllegalStateException fallo = assertThrows(IllegalStateException.class,
                    () -> defectuosa.ejecutar(() -> defectuosa.ejecutar(() -> "nunca")));
            Duration espera = Duration.ofNanos(System.nanoTime() - inicio);
            assertTrue(fallo.getCause().getMessage().contains("Connection is not available, request timed out after"),
                    fallo.getCause().getMessage());
            assertTrue(espera.toMillis() >= 450, "esperó el connectionTimeout entero: " + espera.toMillis() + " ms");

            UnidadDeTrabajoJdbc corregida = new UnidadDeTrabajoJdbc(uno);          // la corrección: se une a la abierta
            assertEquals("listo", corregida.ejecutar(() -> corregida.ejecutar(() -> "listo")));
        }
    }
}
