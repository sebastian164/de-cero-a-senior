package tienda.adaptadores.salida.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import javax.sql.DataSource;
import tienda.aplicacion.puertos.salida.UnidadDeTrabajo;

/**
 * Paso 1: el puerto de transacciones de la tienda sobre JDBC y HikariCP. Es el ÚNICO sitio que pide conexiones al pool
 * y las devuelve. Una unidad de trabajo anidada se une a la que ya está abierta: no pide otra conexión.
 */
public final class UnidadDeTrabajoJdbc implements UnidadDeTrabajo {

    /** Una operación que necesita una conexión. */
    public interface Operacion<T> {

        T con(Connection conexion) throws SQLException;
    }

    /** Lo que vive lo que dura una transacción: su conexión y las versiones que leyó (para el control optimista). */
    private record Transaccion(Connection conexion, Map<String, Long> versiones) {
    }

    private final DataSource pool;
    private final ThreadLocal<Transaccion> enCurso = new ThreadLocal<>();

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
            enCurso.set(new Transaccion(conexion, new HashMap<>()));
            try {
                T resultado = trabajo.get();
                conexion.commit();
                return resultado;
            } catch (RuntimeException | Error | SQLException error) {
                try {
                    conexion.rollback();
                } catch (SQLException alRevertir) {              // con la conexión rota, que no tape el error del caso de uso
                    error.addSuppressed(alRevertir);
                }
                throw error;
            } finally {
                enCurso.remove();
            }
        } catch (SQLException error) {
            throw new IllegalStateException("fallo de base de datos", error);
        }                                                        // al cerrarse, la conexión vuelve al pool
    }

    /** Para los repositorios: usan la conexión de la transacción en curso. Fuera de una transacción, toman una solo para esta operación. */
    public <T> T conConexion(Operacion<T> operacion) {
        try {
            Transaccion deLaTransaccion = enCurso.get();
            if (deLaTransaccion != null) {
                return operacion.con(deLaTransaccion.conexion());
            }
            try (Connection suelta = pool.getConnection()) {
                return operacion.con(suelta);
            }
        } catch (SQLException error) {
            throw new IllegalStateException("fallo de base de datos", error);
        }
    }

    /**
     * La versión de cada fila que esta transacción leyó o escribió. Fuera de una transacción no hay nada que recordar:
     * devuelve un mapa nuevo y vacío, así que un guardar sin unidad de trabajo actualiza SIN el control optimista
     * (sin "and version = ?"). Los casos de uso siempre guardan dentro de ejecutar.
     */
    Map<String, Long> versiones() {
        Transaccion actual = enCurso.get();
        return actual == null ? new HashMap<>() : actual.versiones();
    }
}
