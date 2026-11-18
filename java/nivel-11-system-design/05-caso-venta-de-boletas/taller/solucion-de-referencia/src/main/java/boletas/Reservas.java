package boletas;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;

/**
 * Reserva de asientos con tres estrategias de base de datos. Un asiento vencido (reservado_hasta pasado) cuenta como libre.
 * Cada reserva exitosa ocurre en UNA transacción: toma el asiento y, solo si lo tomó, inserta la fila de reservas.
 * Si el asiento no estaba disponible no queda ninguna fila de reserva (ROLLBACK).
 */
public class Reservas {
    public enum Estrategia { CONDICIONAL, PESIMISTA, OPTIMISTA }
    public static final long SIN_RESERVA = -1;
    public final AtomicLong reintentos = new AtomicLong();   // lecturas repetidas por conflicto de versión (optimista)
    private final DataSource ds;

    public Reservas(DataSource ds) { this.ds = ds; }

    private static final String LIBRE = "(estado = 'LIBRE' OR (estado = 'RESERVADO' AND reservado_hasta < now()))";
    private static final String TOMAR = "UPDATE asientos SET estado='RESERVADO', reserva_id=?, reservado_hasta=now() + (? || ' seconds')::interval, version=version+1 ";

    /** La transacción del README (sesión 11-05, sección 5) tal cual, con :parametros como ?. La prueba la compara con el README. */
    public static final String SQL_README = """
            UPDATE asientos
               SET estado = 'RESERVADO', reserva_id = ?
             WHERE evento_id = ?
               AND asiento_id = ANY(?)
               AND estado = 'LIBRE'""";

    private static long nuevoIdDeReserva(Connection c) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT nextval(pg_get_serial_sequence('reservas','id'))")) {
            r.next(); return r.getLong(1);
        }
    }

    private static void insertarReserva(Connection c, long id, long usuario, long evento, int segundos) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("INSERT INTO reservas(id, usuario_id, evento_id, expira_en) VALUES (?, ?, ?, now() + (? || ' seconds')::interval)")) {
            p.setLong(1, id); p.setLong(2, usuario); p.setLong(3, evento); p.setInt(4, segundos);
            p.executeUpdate();
        }
    }

    /** README tal cual: UPDATE de todos los asientos pedidos, ROLLBACK si faltan filas, INSERT de la reserva, COMMIT. Devuelve la reserva o SIN_RESERVA (409). */
    public long reservarVariosComoEnElReadme(long usuario, long evento, Integer[] asientos, int segundos) throws SQLException {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                long reserva = nuevoIdDeReserva(c);
                int filas;
                try (PreparedStatement p = c.prepareStatement(SQL_README)) {
                    p.setLong(1, reserva); p.setLong(2, evento); p.setArray(3, c.createArrayOf("integer", asientos));
                    filas = p.executeUpdate();
                }
                if (filas < asientos.length) { c.rollback(); return SIN_RESERVA; }
                insertarReserva(c, reserva, usuario, evento, segundos);
                c.commit();
                return reserva;
            } catch (SQLException ex) { c.rollback(); throw ex; }
        }
    }

    /** Lo mismo con vencimiento: los asientos ocupados por reservas vencidas cuentan como libres y quedan con 'reservado_hasta'. */
    public long reservarVarios(long usuario, long evento, Integer[] asientos, int segundos) throws SQLException {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                long reserva = nuevoIdDeReserva(c);
                int filas;
                try (PreparedStatement p = c.prepareStatement(TOMAR + "WHERE evento_id=? AND asiento_id = ANY(?) AND " + LIBRE)) {
                    p.setLong(1, reserva); p.setInt(2, segundos); p.setLong(3, evento); p.setArray(4, c.createArrayOf("integer", asientos));
                    filas = p.executeUpdate();
                }
                if (filas < asientos.length) { c.rollback(); return SIN_RESERVA; }
                insertarReserva(c, reserva, usuario, evento, segundos);
                c.commit();
                return reserva;
            } catch (SQLException ex) { c.rollback(); throw ex; }
        }
    }

    /** Intenta reservar un asiento. Devuelve el id de la reserva creada o SIN_RESERVA si no estaba disponible (sin dejar filas). */
    public long reservar(Estrategia e, long usuario, long evento, int asiento, int segundos) throws SQLException {
        return switch (e) {
            case CONDICIONAL -> reservarVarios(usuario, evento, new Integer[]{asiento}, segundos); // la base es el árbitro: una sentencia atómica
            case PESIMISTA -> pesimista(usuario, evento, asiento, segundos);
            case OPTIMISTA -> optimista(usuario, evento, asiento, segundos);
        };
    }

    /** SELECT ... FOR UPDATE: correcta, pero las peticiones al mismo asiento hacen cola. */
    private long pesimista(long usuario, long evento, int asiento, int segundos) throws SQLException {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                boolean libre;
                try (PreparedStatement s = c.prepareStatement("SELECT " + LIBRE + " FROM asientos WHERE evento_id=? AND asiento_id=? FOR UPDATE")) {
                    s.setLong(1, evento); s.setInt(2, asiento);
                    try (ResultSet r = s.executeQuery()) { libre = r.next() && r.getBoolean(1); }
                }
                if (!libre) { c.rollback(); return SIN_RESERVA; }
                long reserva = nuevoIdDeReserva(c);
                try (PreparedStatement u = c.prepareStatement(TOMAR + "WHERE evento_id=? AND asiento_id=?")) {
                    u.setLong(1, reserva); u.setInt(2, segundos); u.setLong(3, evento); u.setInt(4, asiento);
                    u.executeUpdate();
                }
                insertarReserva(c, reserva, usuario, evento, segundos);
                c.commit();
                return reserva;
            } catch (SQLException ex) { c.rollback(); throw ex; }
        }
    }

    /** Lee la versión y actualiza solo si no cambió; ante un cambio concurrente vuelve a leer (reintento). */
    private long optimista(long usuario, long evento, int asiento, int segundos) throws SQLException {
        while (true) {
            int version; boolean libre;
            try (Connection c = ds.getConnection(); PreparedStatement s = c.prepareStatement(
                    "SELECT version, " + LIBRE + " FROM asientos WHERE evento_id=? AND asiento_id=?")) {
                s.setLong(1, evento); s.setInt(2, asiento);
                try (ResultSet r = s.executeQuery()) { r.next(); version = r.getInt(1); libre = r.getBoolean(2); }
            }
            if (!libre) return SIN_RESERVA;
            try (Connection c = ds.getConnection()) {
                c.setAutoCommit(false);
                try {
                    long reserva = nuevoIdDeReserva(c);
                    int filas;
                    try (PreparedStatement u = c.prepareStatement(TOMAR + "WHERE evento_id=? AND asiento_id=? AND version=?")) {
                        u.setLong(1, reserva); u.setInt(2, segundos); u.setLong(3, evento); u.setInt(4, asiento); u.setInt(5, version);
                        filas = u.executeUpdate();
                    }
                    if (filas == 1) { insertarReserva(c, reserva, usuario, evento, segundos); c.commit(); return reserva; }
                    c.rollback();
                } catch (SQLException ex) { c.rollback(); throw ex; }
            }
            reintentos.incrementAndGet(); // alguien se adelantó: se vuelve a leer
        }
    }

    /**
     * Proceso periódico de expiración (con SKIP LOCKED para poder correr en varias instancias): libera los asientos vencidos
     * y marca EXPIRADA toda reserva ACTIVA cuyo plazo pasó. Devuelve cuántos asientos liberó.
     */
    public int liberarVencidos() throws SQLException {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            int liberados = s.executeUpdate("""
                UPDATE asientos SET estado='LIBRE', reserva_id=NULL, reservado_hasta=NULL, version=version+1
                WHERE (evento_id, asiento_id) IN (
                  SELECT evento_id, asiento_id FROM asientos WHERE estado='RESERVADO' AND reservado_hasta < now()
                  FOR UPDATE SKIP LOCKED)""");
            s.executeUpdate("UPDATE reservas SET estado='EXPIRADA' WHERE estado='ACTIVA' AND expira_en < now()");
            return liberados;
        }
    }

    public String estadoDeReserva(long reserva) throws SQLException {
        try (Connection c = ds.getConnection(); PreparedStatement s = c.prepareStatement("SELECT estado FROM reservas WHERE id=?")) {
            s.setLong(1, reserva);
            try (ResultSet r = s.executeQuery()) { return r.next() ? r.getString(1) : null; }
        }
    }

    public long contarReservas() throws SQLException {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT count(*) FROM reservas")) {
            r.next(); return r.getLong(1);
        }
    }

    public String estadoDe(long evento, int asiento) throws SQLException {
        try (Connection c = ds.getConnection(); PreparedStatement s = c.prepareStatement(
                "SELECT estado || ':' || coalesce(reserva_id::text,'-') FROM asientos WHERE evento_id=? AND asiento_id=?")) {
            s.setLong(1, evento); s.setInt(2, asiento);
            try (ResultSet r = s.executeQuery()) { r.next(); return r.getString(1); }
        }
    }

    public long contar(String estado) throws SQLException {
        try (Connection c = ds.getConnection(); PreparedStatement s = c.prepareStatement("SELECT count(*) FROM asientos WHERE estado=?")) {
            s.setString(1, estado);
            try (ResultSet r = s.executeQuery()) { r.next(); return r.getLong(1); }
        }
    }
}
