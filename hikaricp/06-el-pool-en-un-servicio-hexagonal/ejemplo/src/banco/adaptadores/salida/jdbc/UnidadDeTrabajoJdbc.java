package banco.adaptadores.salida.jdbc;

import banco.aplicacion.puertos.salida.UnidadDeTrabajo;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;
import javax.sql.DataSource;

/** El puerto de transacciones sobre JDBC. Es quien toma la conexión del pool y quien la devuelve. */
public final class UnidadDeTrabajoJdbc implements UnidadDeTrabajo {

    /** Una operación que necesita una conexión. */
    public interface Operacion<T> {

        T con(Connection conexion) throws SQLException;
    }

    private final DataSource pool;
    private final ThreadLocal<Connection> enCurso = new ThreadLocal<>();

    public UnidadDeTrabajoJdbc(DataSource pool) {
        this.pool = pool;
    }

    @Override
    public <T> T ejecutar(Supplier<T> trabajo) {
        if (enCurso.get() != null) {
            return trabajo.get();                                // se une a la transacción abierta: no pide otra conexión
        }
        try (Connection conexion = pool.getConnection()) {       // sale del pool; espera, como mucho, connectionTimeout
            conexion.setAutoCommit(false);
            enCurso.set(conexion);
            try {
                T resultado = trabajo.get();
                conexion.commit();
                return resultado;
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            } finally {
                enCurso.remove();
            }
        } catch (SQLException error) {
            throw new IllegalStateException("fallo de base de datos", error);
        }                                                        // al cerrarse, la conexión vuelve al pool
    }

    /** Para los repositorios: usa la conexión de la transacción en curso o, si no hay, una solo para esta operación. */
    public <T> T conConexion(Operacion<T> operacion) {
        try {
            Connection deLaTransaccion = enCurso.get();
            if (deLaTransaccion != null) {
                return operacion.con(deLaTransaccion);
            }
            try (Connection suelta = pool.getConnection()) {
                return operacion.con(suelta);
            }
        } catch (SQLException error) {
            throw new IllegalStateException("fallo de base de datos", error);
        }
    }
}
