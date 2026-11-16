package banco.adaptadores.salida.jdbc;

import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.puertos.salida.RepositorioDeCuentas;
import banco.dominio.Cuenta;
import banco.dominio.Dinero;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** El repositorio de cuentas con SQL a mano. No abre ni cierra conexiones: usa la de la unidad de trabajo. */
public final class CuentasJdbc implements RepositorioDeCuentas {

    private static final String INSERTAR = "insert into cuenta (titular, saldo_centavos, enviado_hoy_centavos, dia_del_tope, id, version) "
            + "values (?, ?, ?, ?, ?, 1)";
    private static final String ACTUALIZAR = "update cuenta set titular = ?, saldo_centavos = ?, enviado_hoy_centavos = ?, dia_del_tope = ?, "
            + "version = version + 1 where id = ? and version = ?";
    private static final String BUSCAR = "select titular, saldo_centavos, enviado_hoy_centavos, dia_del_tope, version from cuenta where id = ?";

    private final UnidadDeTrabajoJdbc transacciones;

    public CuentasJdbc(UnidadDeTrabajoJdbc transacciones) {
        this.transacciones = transacciones;
    }

    @Override
    public String siguienteId() {
        return "cuenta-" + UUID.randomUUID();
    }

    @Override
    public void guardar(Cuenta cuenta) {
        boolean esNueva = cuenta.version() == 0;
        int filas = transacciones.conConexion(conexion -> {
            try (PreparedStatement sentencia = conexion.prepareStatement(esNueva ? INSERTAR : ACTUALIZAR)) {
                sentencia.setString(1, cuenta.titular());
                sentencia.setLong(2, cuenta.saldo().centavos());
                sentencia.setLong(3, cuenta.enviadoHoy().centavos());
                sentencia.setObject(4, cuenta.diaDelTope());
                sentencia.setString(5, cuenta.id());
                if (!esNueva) {
                    sentencia.setLong(6, cuenta.version());      // el bloqueo optimista, escrito a mano
                }
                return sentencia.executeUpdate();
            }
        });
        if (filas == 0) {
            throw new ConflictoDeConcurrencia("la cuenta " + cuenta.id());
        }
    }

    @Override
    public Optional<Cuenta> buscar(String id) {
        return transacciones.conConexion(conexion -> {
            try (PreparedStatement sentencia = conexion.prepareStatement(BUSCAR)) {
                sentencia.setString(1, id);
                try (ResultSet fila = sentencia.executeQuery()) {
                    if (!fila.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(Cuenta.existente(id, fila.getString("titular"),
                            new Dinero(fila.getLong("saldo_centavos")), new Dinero(fila.getLong("enviado_hoy_centavos")),
                            fila.getObject("dia_del_tope", LocalDate.class), fila.getLong("version")));
                }
            }
        });
    }
}
