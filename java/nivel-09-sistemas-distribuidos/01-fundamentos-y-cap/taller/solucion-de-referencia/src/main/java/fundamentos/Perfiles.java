package fundamentos;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Escribe en el líder y lee de la réplica, con las dos soluciones a "no veo lo que acabo de guardar". */
public class Perfiles {

    private final String lider;
    private final String replica;
    private final Clock reloj;
    private final Map<Long, Instant> ultimaEscritura = new ConcurrentHashMap<>();

    public Perfiles(String lider, String replica, Clock reloj) {
        this.lider = lider;
        this.replica = replica;
        this.reloj = reloj;
    }

    private static Connection conectar(String url) throws SQLException {
        return DriverManager.getConnection(url, "curso", "curso");
    }

    /** Guarda en el líder y devuelve la posición del WAL tras confirmar: la "versión esperada" que se envía al cliente. */
    public String guardar(long usuario, String nombre) throws SQLException {
        try (Connection c = conectar(lider);
             PreparedStatement ps = c.prepareStatement("UPDATE perfiles SET nombre = ? WHERE usuario = ?")) {
            ps.setString(1, nombre);
            ps.setLong(2, usuario);
            ps.executeUpdate();
            ultimaEscritura.put(usuario, reloj.instant());
            try (ResultSet rs = c.createStatement().executeQuery("SELECT pg_current_wal_lsn()::text")) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    /** Lectura ingenua: siempre de la réplica. */
    public String leerDeReplica(long usuario) throws SQLException {
        return leer(replica, usuario);
    }

    /** Solución 1: durante unos segundos tras escribir, ese usuario lee del líder. */
    public String leerConLiderTrasEscribir(long usuario, Duration ventana) throws SQLException {
        Instant escribio = ultimaEscritura.get(usuario);
        boolean reciente = escribio != null && reloj.instant().isBefore(escribio.plus(ventana));
        return leer(reciente ? lider : replica, usuario);
    }

    /** Solución 2: el cliente envía la versión que espera; la réplica responde solo cuando la alcanzó. */
    public String leerConVersionEsperada(long usuario, String lsnEsperado, Duration espera) throws SQLException, InterruptedException {
        Instant limite = Instant.now().plus(espera);
        try (Connection c = conectar(replica);
             PreparedStatement ps = c.prepareStatement("SELECT pg_last_wal_replay_lsn() >= ?::pg_lsn")) {
            ps.setString(1, lsnEsperado);
            while (Instant.now().isBefore(limite)) {
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getBoolean(1)) {
                        return leer(replica, usuario);
                    }
                }
                Thread.sleep(50);
            }
        }
        return leer(lider, usuario);       // la réplica no alcanzó a tiempo: se lee del líder
    }

    private static String leer(String url, long usuario) throws SQLException {
        try (Connection c = conectar(url);
             PreparedStatement ps = c.prepareStatement("SELECT nombre FROM perfiles WHERE usuario = ?")) {
            ps.setLong(1, usuario);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}
