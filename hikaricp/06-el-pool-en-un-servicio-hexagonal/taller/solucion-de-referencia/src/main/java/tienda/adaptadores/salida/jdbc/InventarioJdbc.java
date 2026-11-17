package tienda.adaptadores.salida.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import tienda.aplicacion.ConflictoDeConcurrencia;
import tienda.aplicacion.puertos.salida.Inventario;
import tienda.dominio.Existencias;

/** Paso 2: Inventario con JDBC, con la conexión de la transacción en curso y control optimista con la columna version. */
public final class InventarioJdbc implements Inventario {

    private final UnidadDeTrabajoJdbc transacciones;

    public InventarioJdbc(UnidadDeTrabajoJdbc transacciones) {
        this.transacciones = transacciones;
    }

    @Override
    public Optional<Existencias> existenciasDe(String producto) {
        return transacciones.conConexion(conexion -> {
            try (PreparedStatement sentencia = conexion.prepareStatement("select unidades, version from existencias where producto = ?")) {
                sentencia.setString(1, producto);
                try (ResultSet fila = sentencia.executeQuery()) {
                    if (!fila.next()) {
                        return Optional.<Existencias>empty();
                    }
                    transacciones.versiones().put("existencias:" + producto, fila.getLong("version"));
                    return Optional.of(Existencias.de(producto, fila.getInt("unidades")));
                }
            }
        });
    }

    @Override
    public void guardar(Existencias existencias) {
        String clave = "existencias:" + existencias.producto();
        transacciones.conConexion(conexion -> {
            Long leida = transacciones.versiones().get(clave);
            String sql = "update existencias set unidades = ?, version = version + 1 where producto = ?" + (leida == null ? "" : " and version = ?")
                    + " returning version";
            try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
                sentencia.setInt(1, existencias.unidades());
                sentencia.setString(2, existencias.producto());
                if (leida != null) {
                    sentencia.setLong(3, leida);
                }
                try (ResultSet fila = sentencia.executeQuery()) {
                    if (fila.next()) {
                        transacciones.versiones().put(clave, fila.getLong(1));
                        return null;
                    }
                }
            }
            if (leida != null) {
                throw new ConflictoDeConcurrencia("las existencias de " + existencias.producto());
            }
            try (PreparedStatement sentencia = conexion.prepareStatement("insert into existencias (producto, unidades, version) values (?, ?, 0)")) {
                sentencia.setString(1, existencias.producto());
                sentencia.setInt(2, existencias.unidades());
                sentencia.executeUpdate();
            }
            transacciones.versiones().put(clave, 0L);
            return null;
        });
    }
}
