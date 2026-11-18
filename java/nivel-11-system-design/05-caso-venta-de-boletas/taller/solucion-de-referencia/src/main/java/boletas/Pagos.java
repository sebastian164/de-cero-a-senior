package boletas;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;

/**
 * Pago con clave de idempotencia y el caso difícil: el pago se aprueba cuando la reserva ya expiró.
 * Política elegida (las dos que sugiere el README, programadas):
 *  1. al INICIAR el pago se extiende la reserva (el usuario no pierde el asiento mientras la pasarela responde);
 *  2. si aun así el pago se aprueba tarde: si el asiento sigue libre o es de esta reserva, se vende;
 *     si lo tomó otra persona, se REEMBOLSA automáticamente y el asiento de la otra persona no se toca.
 */
public class Pagos {
    private final DataSource ds;

    public Pagos(DataSource ds) { this.ds = ds; }

    /** Extiende el asiento y la reserva en UNA transacción: si la segunda sentencia falla, la primera se deshace y los dos plazos no quedan desalineados. */
    public void iniciarPago(long reserva, long evento, int asiento, int extensionSegundos) throws SQLException {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement p = c.prepareStatement(
                        "UPDATE asientos SET reservado_hasta = now() + (? || ' seconds')::interval WHERE evento_id=? AND asiento_id=? AND reserva_id=? AND estado='RESERVADO'")) {
                    p.setInt(1, extensionSegundos); p.setLong(2, evento); p.setInt(3, asiento); p.setLong(4, reserva);
                    p.executeUpdate();
                }
                try (PreparedStatement p = c.prepareStatement(
                        "UPDATE reservas SET expira_en = now() + (? || ' seconds')::interval WHERE id=? AND estado='ACTIVA'")) {
                    p.setInt(1, extensionSegundos); p.setLong(2, reserva);
                    p.executeUpdate();
                }
                c.commit();
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        }
    }

    /** Resultado: CONFIRMADO, REEMBOLSADO o RECHAZADO. Repetir la misma clave devuelve el mismo resultado sin repetir efectos. */
    public String confirmar(long reserva, long evento, int asiento, String clave, boolean aprobadoPorLaPasarela) throws SQLException {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ya = c.prepareStatement("SELECT estado FROM pagos WHERE clave_idempotencia=?")) {
                    ya.setString(1, clave);
                    try (ResultSet r = ya.executeQuery()) { if (r.next()) { c.rollback(); return r.getString(1); } }
                }
                String resultado;
                if (!aprobadoPorLaPasarela) {
                    resultado = "RECHAZADO";
                } else {
                    int vendidos;
                    // se vende si el asiento es de esta reserva (aunque haya vencido) o si está libre porque se liberó y nadie lo tomó
                    try (PreparedStatement v = c.prepareStatement("""
                            UPDATE asientos SET estado='VENDIDO', reserva_id=?, reservado_hasta=NULL, version=version+1
                            WHERE evento_id=? AND asiento_id=? AND (reserva_id=? OR estado='LIBRE') AND estado <> 'VENDIDO'""")) {
                        v.setLong(1, reserva); v.setLong(2, evento); v.setInt(3, asiento); v.setLong(4, reserva);
                        vendidos = v.executeUpdate();
                    }
                    resultado = vendidos == 1 ? "CONFIRMADO" : "REEMBOLSADO";
                }
                try (PreparedStatement i = c.prepareStatement("INSERT INTO pagos(reserva_id, clave_idempotencia, estado) VALUES (?,?,?)")) {
                    i.setLong(1, reserva); i.setString(2, clave); i.setString(3, resultado);
                    i.executeUpdate();
                }
                String estadoReserva = switch (resultado) { case "CONFIRMADO" -> "CONFIRMADA"; case "REEMBOLSADO" -> "REEMBOLSADA"; default -> null; };
                if (estadoReserva != null) {
                    try (PreparedStatement u = c.prepareStatement("UPDATE reservas SET estado=? WHERE id=?")) {
                        u.setString(1, estadoReserva); u.setLong(2, reserva);
                        u.executeUpdate();
                    }
                }
                c.commit();
                return resultado;
            } catch (SQLException ex) {
                c.rollback();
                if ("23505".equals(ex.getSQLState())) return confirmar(reserva, evento, asiento, clave, aprobadoPorLaPasarela); // carrera por la misma clave
                throw ex;
            }
        }
    }
}
