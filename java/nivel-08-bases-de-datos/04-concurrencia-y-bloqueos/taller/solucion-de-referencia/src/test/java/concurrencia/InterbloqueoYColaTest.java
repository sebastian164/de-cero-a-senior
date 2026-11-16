package concurrencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import soporte.Entorno;

/** Pasos 4 y 5, en JDBC puro sobre PostgreSQL. */
class InterbloqueoYColaTest {

    static String url;

    @BeforeAll
    static void base() throws Exception {
        url = Entorno.baseNueva("n8_04_jdbc");
    }

    static void sql(Connection c, String s) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute(s);
        }
    }

    void cuentas() throws Exception {
        try (Connection c = Entorno.conectar(url)) {
            sql(c, "DROP TABLE IF EXISTS cuentas; CREATE TABLE cuentas (id INT PRIMARY KEY, saldo INT NOT NULL);"
                    + "INSERT INTO cuentas VALUES (1, 1000), (2, 1000)");
        }
    }

    /** Transfiere bloqueando primero el origen y después el destino, con una barrera para forzar el cruce. */
    String transferir(int origen, int destino, boolean ordenar, CyclicBarrier barrera) {
        try (Connection c = Entorno.conectar(url)) {
            c.setAutoCommit(false);
            int primero = ordenar ? Math.min(origen, destino) : origen;
            int segundo = ordenar ? Math.max(origen, destino) : destino;
            sql(c, "SELECT saldo FROM cuentas WHERE id = " + primero + " FOR UPDATE");
            if (!ordenar) {
                barrera.await();     // los dos ya tienen su primer bloqueo
            }
            sql(c, "SELECT saldo FROM cuentas WHERE id = " + segundo + " FOR UPDATE");
            sql(c, "UPDATE cuentas SET saldo = saldo - 100 WHERE id = " + origen);
            sql(c, "UPDATE cuentas SET saldo = saldo + 100 WHERE id = " + destino);
            c.commit();
            return "OK";
        } catch (SQLException e) {
            return e.getSQLState();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void paso4_transferencias_cruzadas_provocan_un_interbloqueo() throws Exception {
        cuentas();
        CyclicBarrier barrera = new CyclicBarrier(2);
        List<String> resultados = new ArrayList<>(List.of("", ""));
        Thread a = new Thread(() -> resultados.set(0, transferir(1, 2, false, barrera)));
        Thread b = new Thread(() -> resultados.set(1, transferir(2, 1, false, barrera)));
        a.start(); b.start(); a.join(); b.join();
        System.out.println("Sin orden: " + resultados);
        assertThat(resultados).containsExactlyInAnyOrder("OK", "40P01");   // deadlock_detected: la base aborta a una
    }

    @Test
    void paso4_bloqueando_por_id_ascendente_no_hay_interbloqueo() throws Exception {
        cuentas();
        Paralelo.Resultado r = Paralelo.correr(40, i -> {
            String s = i % 2 == 0 ? transferir(1, 2, true, null) : transferir(2, 1, true, null);
            assertThat(s).isEqualTo("OK");
        });
        assertThat(r.fallos()).isZero();
        try (Connection c = Entorno.conectar(url); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT sum(saldo) FROM cuentas")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(2000);
        }
    }

    @Test
    void paso5_cola_con_skip_locked_y_tres_trabajadores() throws Exception {
        try (Connection c = Entorno.conectar(url)) {
            sql(c, """
                DROP TABLE IF EXISTS tareas, procesadas;
                CREATE TABLE tareas (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
                                     creada_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp());
                CREATE TABLE procesadas (tarea_id BIGINT PRIMARY KEY, trabajador INT NOT NULL);   -- PK: un duplicado fallaría
                INSERT INTO tareas (estado) SELECT 'PENDIENTE' FROM generate_series(1, 300);
                """);
        }
        Map<Integer, AtomicInteger> porTrabajador = new ConcurrentHashMap<>();
        Paralelo.Resultado r = Paralelo.correr(3, trabajador -> {
            try (Connection c = Entorno.conectar(url)) {
                c.setAutoCommit(false);
                while (true) {
                    List<Long> ids = new ArrayList<>();
                    try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("""
                            SELECT id FROM tareas
                            WHERE estado = 'PENDIENTE'
                            ORDER BY creada_en
                            LIMIT 10
                            FOR UPDATE SKIP LOCKED""")) {
                        while (rs.next()) {
                            ids.add(rs.getLong(1));
                        }
                    }
                    if (ids.isEmpty()) {
                        c.commit();
                        return;
                    }
                    Array arreglo = c.createArrayOf("bigint", ids.toArray());
                    try (PreparedStatement ps = c.prepareStatement("UPDATE tareas SET estado = 'EN_PROCESO' WHERE id = ANY(?)")) {
                        ps.setArray(1, arreglo);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = c.prepareStatement("INSERT INTO procesadas SELECT unnest(?), ?")) {
                        ps.setArray(1, arreglo);
                        ps.setInt(2, trabajador);
                        ps.executeUpdate();
                    }
                    Thread.sleep(2);    // "procesar" el lote
                    c.commit();
                    porTrabajador.computeIfAbsent(trabajador, k -> new AtomicInteger()).addAndGet(ids.size());
                }
            }
        });
        assertThat(r.fallos()).isZero();
        try (Connection c = Entorno.conectar(url); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*), count(DISTINCT tarea_id) FROM procesadas")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(300);
            assertThat(rs.getInt(2)).isEqualTo(300);
        }
        System.out.println("SKIP LOCKED, tareas por trabajador: " + porTrabajador);
        assertThat(porTrabajador).hasSize(3);
        assertThat(porTrabajador.values()).allMatch(n -> n.get() > 0);
    }
}
