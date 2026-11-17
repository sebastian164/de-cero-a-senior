package inventario.adaptadores.salida.jdbc;

import comun.Kf;
import inventario.aplicacion.Puertos.UnidadDeTrabajo;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.function.Supplier;

/** Una transacción de PostgreSQL por unidad de trabajo. Esquema propio: k08i (la base de Inventario). */
public final class UnidadJdbc implements UnidadDeTrabajo {

    private final ThreadLocal<Connection> actual = new ThreadLocal<>();

    public static Connection nueva() throws SQLException {
        Connection c = DriverManager.getConnection(Kf.pgUrl() + "?currentSchema=k08i&options=-c%20lock_timeout%3D1000", "taller", "taller");
        c.setAutoCommit(false);
        return c;
    }

    Connection conexion() {
        Connection c = actual.get();
        if (c == null) throw new IllegalStateException("escritura fuera de una unidad de trabajo");
        return c;
    }

    @Override
    public <T> T ejecutar(Supplier<T> trabajo) {
        if (actual.get() != null) return trabajo.get();
        try (Connection c = nueva()) {
            actual.set(c);
            try {
                T r = trabajo.get();
                c.commit();
                return r;
            } catch (RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                actual.remove();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void esquemaNuevo(int existenciasIniciales) throws SQLException {
        try (Connection c = DriverManager.getConnection(Kf.pgUrl(), "taller", "taller"); var st = c.createStatement()) {
            st.execute("CREATE SCHEMA IF NOT EXISTS k08i");
            st.execute("DROP TABLE IF EXISTS k08i.stock, k08i.reservas, k08i.vistos");
            st.execute("CREATE TABLE k08i.stock(producto text PRIMARY KEY, disponibles int NOT NULL CHECK (disponibles >= 0))");
            st.execute("CREATE TABLE k08i.reservas(pedido_id text, producto text, cantidad int)");
            st.execute("CREATE TABLE k08i.vistos(evento_id text PRIMARY KEY)");
            st.execute("INSERT INTO k08i.stock VALUES ('libro', " + existenciasIniciales + ")");
        }
    }
}
