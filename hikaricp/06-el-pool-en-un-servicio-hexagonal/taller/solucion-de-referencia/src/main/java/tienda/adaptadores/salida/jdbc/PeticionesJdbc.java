package tienda.adaptadores.salida.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import tienda.aplicacion.ConflictoDeConcurrencia;
import tienda.aplicacion.puertos.salida.PeticionesProcesadas;

/** Paso 2: PeticionesProcesadas con JDBC. La clave primaria impide atender dos veces la misma petición. */
public final class PeticionesJdbc implements PeticionesProcesadas {

    private final UnidadDeTrabajoJdbc transacciones;

    public PeticionesJdbc(UnidadDeTrabajoJdbc transacciones) {
        this.transacciones = transacciones;
    }

    @Override
    public Optional<String> pedidoDe(String clave) {
        return transacciones.conConexion(conexion -> {
            try (PreparedStatement sentencia = conexion.prepareStatement("select pedido_id from peticion_procesada where clave = ?")) {
                sentencia.setString(1, clave);
                try (ResultSet fila = sentencia.executeQuery()) {
                    return fila.next() ? Optional.of(fila.getString(1)) : Optional.<String>empty();
                }
            }
        });
    }

    @Override
    public void registrar(String clave, String pedidoId) {
        int insertadas = transacciones.conConexion(conexion -> {
            try (PreparedStatement sentencia = conexion.prepareStatement(
                    "insert into peticion_procesada (clave, pedido_id) values (?, ?) on conflict do nothing")) {
                sentencia.setString(1, clave);
                sentencia.setString(2, pedidoId);
                return sentencia.executeUpdate();
            }
        });
        if (insertadas == 0) {
            throw new ConflictoDeConcurrencia("la petición " + clave);   // el reintento encontrará el pedido de la otra
        }
    }
}
