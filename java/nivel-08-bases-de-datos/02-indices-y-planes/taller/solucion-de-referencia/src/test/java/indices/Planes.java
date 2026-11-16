package indices;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ejecuta EXPLAIN ANALYZE y extrae el plan y el tiempo real de ejecución. */
final class Planes {

    record Plan(String texto, double milisegundos) {
        boolean usa(String nodo) { return texto.contains(nodo); }
        String primeraLinea() { return texto.lines().findFirst().orElse(""); }
    }

    private static final Pattern TIEMPO = Pattern.compile("Execution Time: ([0-9.]+) ms");

    static Plan explicar(Connection c, String sql) throws SQLException {
        Plan mejor = null;
        for (int i = 0; i < 3; i++) {       // tres corridas, se queda con la más rápida (caché caliente)
            StringBuilder sb = new StringBuilder();
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("EXPLAIN (ANALYZE, BUFFERS) " + sql)) {
                while (rs.next()) {
                    sb.append(rs.getString(1)).append('\n');
                }
            }
            Matcher m = TIEMPO.matcher(sb);
            if (!m.find()) {
                throw new IllegalStateException("Plan sin tiempo:\n" + sb);
            }
            Plan p = new Plan(sb.toString(), Double.parseDouble(m.group(1)));
            if (mejor == null || p.milisegundos() < mejor.milisegundos()) {
                mejor = p;
            }
        }
        return mejor;
    }

    static void ejecutar(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }
}
