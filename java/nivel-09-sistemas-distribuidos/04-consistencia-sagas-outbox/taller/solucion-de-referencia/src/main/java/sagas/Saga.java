package sagas;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import soporte.Entorno;

/** Orquestador de una saga de tres pasos con estado persistido en PostgreSQL y compensaciones reintentables. */
public final class Saga {

    /** Un servicio participante. Las dos operaciones deben ser idempotentes. */
    public interface Participante {
        String nombre();
        void ejecutar(long pedidoId);
        void compensar(long pedidoId);
    }

    public static final String ESQUEMA = """
        CREATE TABLE IF NOT EXISTS sagas (pedido_id BIGINT PRIMARY KEY, estado TEXT NOT NULL, paso TEXT, detalle TEXT);
        """;

    private final String url;
    private final List<Participante> pasos;
    private final int intentosDeCompensacion;

    public Saga(String url, List<Participante> pasos, int intentosDeCompensacion) {
        this.url = url;
        this.pasos = pasos;
        this.intentosDeCompensacion = intentosDeCompensacion;
    }

    /** Ejecuta la saga. Devuelve el estado final: COMPLETADA, CANCELADA o COMPENSACION_PENDIENTE. */
    public String ejecutar(long pedidoId) throws SQLException {
        guardar(pedidoId, "EN_CURSO", null, null);
        return avanzar(pedidoId, 0);
    }

    /** Ejecuta los pasos desde el índice dado. Si uno falla, compensa los anteriores a él. */
    private String avanzar(long pedidoId, int desde) throws SQLException {
        for (int i = desde; i < pasos.size(); i++) {
            Participante p = pasos.get(i);
            guardar(pedidoId, "EN_CURSO", p.nombre(), "ejecutando");
            try {
                p.ejecutar(pedidoId);
                guardar(pedidoId, "EN_CURSO", p.nombre(), "hecho");
            } catch (RuntimeException e) {
                guardar(pedidoId, "COMPENSANDO", p.nombre(), "falló: " + e.getMessage());
                return compensar(pedidoId, i);
            }
        }
        guardar(pedidoId, "COMPLETADA", null, null);
        return "COMPLETADA";
    }

    /** Compensa en orden inverso los pasos 0..hechos-1, anotando cada compensación terminada. */
    private String compensar(long pedidoId, int hechos) throws SQLException {
        for (int i = hechos - 1; i >= 0; i--) {                           // orden inverso
            Participante p = pasos.get(i);
            boolean ok = false;
            for (int intento = 1; intento <= intentosDeCompensacion && !ok; intento++) {
                try {
                    p.compensar(pedidoId);
                    ok = true;
                } catch (RuntimeException e) {
                    guardar(pedidoId, "COMPENSANDO", p.nombre(), "compensación falló (intento " + intento + "): " + e.getMessage());
                }
            }
            if (!ok) {
                guardar(pedidoId, "COMPENSACION_PENDIENTE", p.nombre(), "requiere intervención o reintento posterior");
                return "COMPENSACION_PENDIENTE";
            }
            guardar(pedidoId, "COMPENSANDO", p.nombre(), "compensado");
        }
        guardar(pedidoId, "CANCELADA", null, null);
        return "CANCELADA";
    }

    /**
     * Retoma una saga desde el estado persistido (tras la caída del orquestador, o con una compensación pendiente).
     * Se apoya en que ejecutar y compensar son idempotentes: un paso que quedó "ejecutando" se vuelve a ejecutar
     * y una compensación que no quedó anotada como "compensado" se repite.
     */
    public String reanudar(long pedidoId) throws SQLException {
        String estado, paso, detalle;
        try (Connection c = Entorno.conectar(url);
             PreparedStatement ps = c.prepareStatement("SELECT estado, paso, detalle FROM sagas WHERE pedido_id = ?")) {
            ps.setLong(1, pedidoId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return "desconocido";
            estado = rs.getString(1); paso = rs.getString(2); detalle = rs.getString(3);
        }
        int i = paso == null ? -1 : indice(paso);
        switch (estado) {
            case "COMPLETADA", "CANCELADA":
                return estado;
            case "EN_CURSO":
                if (i < 0) return avanzar(pedidoId, 0);
                return avanzar(pedidoId, "hecho".equals(detalle) ? i + 1 : i);         // "ejecutando": se repite el paso
            case "COMPENSANDO":
                if (detalle != null && detalle.startsWith("falló:")) return compensar(pedidoId, i);          // el paso i no llegó a hacerse
                if ("compensado".equals(detalle)) return compensar(pedidoId, i);                             // el paso i ya está deshecho
                return compensar(pedidoId, i + 1);                                                          // compensación fallida: se repite desde i
            case "COMPENSACION_PENDIENTE":
                return compensar(pedidoId, i + 1);
            default:
                throw new IllegalStateException("estado desconocido: " + estado);
        }
    }

    private int indice(String paso) {
        for (int i = 0; i < pasos.size(); i++) if (pasos.get(i).nombre().equals(paso)) return i;
        throw new IllegalStateException("la saga guardada habla de un paso que ya no existe: " + paso);
    }

    private void guardar(long pedidoId, String estado, String paso, String detalle) throws SQLException {
        try (Connection c = Entorno.conectar(url);
             PreparedStatement ps = c.prepareStatement("""
                 INSERT INTO sagas (pedido_id, estado, paso, detalle) VALUES (?, ?, ?, ?)
                 ON CONFLICT (pedido_id) DO UPDATE SET estado = EXCLUDED.estado, paso = EXCLUDED.paso, detalle = EXCLUDED.detalle""")) {
            ps.setLong(1, pedidoId);
            ps.setString(2, estado);
            ps.setString(3, paso);
            ps.setString(4, detalle);
            ps.executeUpdate();
        }
    }

    /** "¿En qué paso está el pedido 42?": una sola consulta. */
    public static String dondeEsta(String url, long pedidoId) throws SQLException {
        try (Connection c = Entorno.conectar(url);
             PreparedStatement ps = c.prepareStatement("SELECT estado || ' / ' || coalesce(paso, '-') || ' / ' || coalesce(detalle, '-') FROM sagas WHERE pedido_id = ?")) {
            ps.setLong(1, pedidoId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString(1) : "desconocido";
        }
    }
}
