package boletas;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

public final class Esquema {
    private Esquema() { }

    public static void recrear(DataSource ds, int asientos) throws SQLException {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS pagos, reservas, asientos");
            s.execute("""
                CREATE TABLE asientos (
                  evento_id BIGINT NOT NULL, asiento_id INT NOT NULL,
                  estado TEXT NOT NULL DEFAULT 'LIBRE' CHECK (estado IN ('LIBRE','RESERVADO','VENDIDO')),
                  reserva_id BIGINT, reservado_hasta TIMESTAMPTZ, version INT NOT NULL DEFAULT 0,
                  PRIMARY KEY (evento_id, asiento_id))""");
            s.execute("""
                CREATE TABLE reservas (
                  id BIGSERIAL PRIMARY KEY, usuario_id BIGINT NOT NULL, evento_id BIGINT NOT NULL,
                  estado TEXT NOT NULL DEFAULT 'ACTIVA', expira_en TIMESTAMPTZ NOT NULL)""");
            s.execute("""
                CREATE TABLE pagos (
                  id BIGSERIAL PRIMARY KEY, reserva_id BIGINT NOT NULL, clave_idempotencia TEXT NOT NULL UNIQUE,
                  estado TEXT NOT NULL)""");
            s.execute("INSERT INTO asientos(evento_id, asiento_id) SELECT 1, g FROM generate_series(1, " + asientos + ") g");
        }
    }
}
