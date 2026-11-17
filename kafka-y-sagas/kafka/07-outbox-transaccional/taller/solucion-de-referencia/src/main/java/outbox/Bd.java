package outbox;

import comun.Kf;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** Conexión y esquema del servicio de Pedidos (esquema k07 de la base compartida del taller). */
public final class Bd {

    private Bd() {
    }

    public static Connection conectar() throws SQLException {
        Connection c = DriverManager.getConnection(Kf.pgUrl() + "?currentSchema=k07", "taller", "taller");
        c.setAutoCommit(false);
        return c;
    }

    public static void esquemaNuevo() throws SQLException {
        try (Connection c = DriverManager.getConnection(Kf.pgUrl(), "taller", "taller"); Statement st = c.createStatement()) {
            st.execute("CREATE SCHEMA IF NOT EXISTS k07");
            st.execute("DROP TABLE IF EXISTS k07.pedidos, k07.salida, k07.efectos, k07.procesados");
            st.execute("CREATE TABLE k07.pedidos(id text PRIMARY KEY, cliente text NOT NULL, estado text NOT NULL)");
            // La tabla de salida: el id del evento nace con la fila; seq da el orden global de inserción
            st.execute("""
                    CREATE TABLE k07.salida(
                      id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                      seq bigserial NOT NULL UNIQUE,
                      agregado text NOT NULL,
                      tipo text NOT NULL,
                      payload text NOT NULL,
                      creada timestamptz NOT NULL DEFAULT clock_timestamp(),
                      publicada timestamptz)""");
            st.execute("CREATE INDEX salida_pendientes ON k07.salida(seq) WHERE publicada IS NULL");
            st.execute("CREATE TABLE k07.procesados(evento_id text PRIMARY KEY)");
            st.execute("CREATE TABLE k07.efectos(pedido_id text, tipo text, evento_id text)");
        }
    }
}
