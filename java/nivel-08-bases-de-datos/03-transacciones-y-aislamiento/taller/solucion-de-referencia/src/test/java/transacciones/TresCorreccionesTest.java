package transacciones;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import soporte.Entorno;

/** Paso 3: las tres correcciones de la actualización perdida, con 20 hilos que venden una unidad cada uno. */
class TresCorreccionesTest {

    static String url;
    static final int HILOS = 20;
    static final StringBuilder resumen = new StringBuilder();

    @BeforeAll
    static void base() {
        url = Entorno.baseNueva("n8_03_correcciones");
    }

    @BeforeEach
    void preparar() throws Exception {
        try (Sesion s = new Sesion(url)) {
            s.sql("DROP TABLE IF EXISTS productos; CREATE TABLE productos (id INT PRIMARY KEY, stock INT NOT NULL, version INT NOT NULL);"
                    + "INSERT INTO productos VALUES (7, 100, 5)");
        }
    }

    @AfterAll
    static void imprimir() {
        System.out.println(resumen);
    }

    interface Venta {
        void vender(Connection c, AtomicInteger reintentos) throws Exception;
    }

    int concurrente(String nombre, Venta venta) throws Exception {
        AtomicInteger reintentos = new AtomicInteger();
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(HILOS);
        List<Future<?>> tareas = new ArrayList<>();
        long t0 = System.nanoTime();
        for (int i = 0; i < HILOS; i++) {
            tareas.add(pool.submit(() -> {
                try (Connection c = Entorno.conectar(url)) {
                    salida.await();
                    venta.vender(c, reintentos);
                }
                return null;
            }));
        }
        salida.countDown();
        for (Future<?> f : tareas) {
            f.get();
        }
        pool.shutdown();
        double ms = (System.nanoTime() - t0) / 1e6;
        try (Sesion s = new Sesion(url)) {
            int stock = s.entero("SELECT stock FROM productos WHERE id = 7");
            resumen.append(String.format(Locale.ROOT, "%-28s stock final %d (esperado 80), reintentos %d, %.0f ms%n",
                    nombre, stock, reintentos.get(), ms));
            return stock;
        }
    }

    static int leerStock(Connection c, String sql) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    static int actualizar(Connection c, String sql) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            return ps.executeUpdate();
        }
    }

    @Test
    void sin_proteccion_se_pierden_ventas() throws Exception {
        int stock = concurrente("leer-calcular-escribir", (c, r) -> {
            c.setAutoCommit(false);
            int s = leerStock(c, "SELECT stock FROM productos WHERE id = 7");
            Thread.sleep(5);   // el tiempo que la aplicación tarda en "calcular"
            actualizar(c, "UPDATE productos SET stock = " + (s - 1) + " WHERE id = 7");
            c.commit();
        });
        assertThat(stock).isGreaterThan(80);
    }

    @Test
    void correccion_1_operacion_atomica() throws Exception {
        int stock = concurrente("1. UPDATE atómico", (c, r) ->
                assertThat(actualizar(c, "UPDATE productos SET stock = stock - 1 WHERE id = 7 AND stock >= 1")).isEqualTo(1));
        assertThat(stock).isEqualTo(80);
    }

    @Test
    void correccion_2_bloqueo_pesimista() throws Exception {
        int stock = concurrente("2. SELECT ... FOR UPDATE", (c, r) -> {
            c.setAutoCommit(false);
            int s = leerStock(c, "SELECT stock FROM productos WHERE id = 7 FOR UPDATE");
            Thread.sleep(5);
            actualizar(c, "UPDATE productos SET stock = " + (s - 1) + " WHERE id = 7");
            c.commit();
        });
        assertThat(stock).isEqualTo(80);
    }

    @Test
    void correccion_3_bloqueo_optimista_con_version() throws Exception {
        int stock = concurrente("3. columna de versión", (c, r) -> {
            while (true) {
                int s;
                int v;
                try (PreparedStatement ps = c.prepareStatement("SELECT stock, version FROM productos WHERE id = 7");
                     ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    s = rs.getInt(1);
                    v = rs.getInt(2);
                }
                Thread.sleep(5);
                int filas = actualizar(c, "UPDATE productos SET stock = " + (s - 1) + ", version = " + (v + 1)
                        + " WHERE id = 7 AND version = " + v);
                if (filas == 1) {
                    return;
                }
                r.incrementAndGet();   // 0 filas: alguien cambió el registro; se vuelve a leer y se reintenta
            }
        });
        assertThat(stock).isEqualTo(80);
    }
}
