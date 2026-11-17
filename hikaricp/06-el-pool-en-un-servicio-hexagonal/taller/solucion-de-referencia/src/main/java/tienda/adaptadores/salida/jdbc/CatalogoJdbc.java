package tienda.adaptadores.salida.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import tienda.aplicacion.puertos.salida.Catalogo;
import tienda.dominio.Dinero;

/** Paso 2: Catalogo con JDBC. Una lectura que usa la conexión de la transacción en curso. */
public final class CatalogoJdbc implements Catalogo {

    private final UnidadDeTrabajoJdbc transacciones;

    public CatalogoJdbc(UnidadDeTrabajoJdbc transacciones) {
        this.transacciones = transacciones;
    }

    @Override
    public Optional<Dinero> precioDe(String producto) {
        return transacciones.conConexion(conexion -> {
            try (PreparedStatement sentencia = conexion.prepareStatement("select precio_centavos from producto where id = ?")) {
                sentencia.setString(1, producto);
                try (ResultSet fila = sentencia.executeQuery()) {
                    return fila.next() ? Optional.of(new Dinero(fila.getLong(1))) : Optional.<Dinero>empty();
                }
            }
        });
    }
}
