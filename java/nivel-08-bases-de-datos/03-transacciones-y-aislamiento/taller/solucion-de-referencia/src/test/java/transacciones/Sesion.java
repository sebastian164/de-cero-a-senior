package transacciones;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import soporte.Entorno;

/** Una sesión de base de datos, como una ventana de psql. Las pruebas intercalan dos a mano, paso a paso. */
final class Sesion implements AutoCloseable {
    private final Connection c;

    Sesion(String url) throws SQLException {
        c = Entorno.conectar(url);
        c.setAutoCommit(true);
    }

    void sql(String s) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute(s);
        }
    }

    int entero(String consulta) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(consulta)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Ejecuta y devuelve el SQLSTATE si falla, o "OK". */
    String intentar(String s) {
        try {
            sql(s);
            return "OK";
        } catch (SQLException e) {
            return e.getSQLState();
        }
    }

    @Override
    public void close() throws SQLException {
        c.close();
    }
}
