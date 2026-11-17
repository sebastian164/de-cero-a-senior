package tienda.adaptadores.salida.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;
import javax.sql.DataSource;
import tienda.aplicacion.puertos.salida.UnidadDeTrabajo;

/** El defecto del paso 9, solo para la prueba: cada unidad de trabajo pide su propia conexión, también la anidada. */
final class UnidadDeTrabajoQueSiemprePideOtra implements UnidadDeTrabajo {

    private final DataSource pool;

    UnidadDeTrabajoQueSiemprePideOtra(DataSource pool) {
        this.pool = pool;
    }

    @Override
    public <T> T ejecutar(Supplier<T> trabajo) {
        try (Connection conexion = pool.getConnection()) {
            return trabajo.get();
        } catch (SQLException error) {
            throw new IllegalStateException("fallo de base de datos", error);
        }
    }
}
