package outbox;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** El consumidor de la sesión 06 en pequeño: el efecto y la marca del evento procesado en la misma transacción. */
public final class ConsumidorIdempotente {

    private ConsumidorIdempotente() {
    }

    /** Devuelve true si aplicó el efecto, false si el evento ya estaba procesado. */
    public static boolean aplicar(String eventoId, String pedidoId, String tipo) throws SQLException {
        try (Connection c = Bd.conectar()) {
            try (PreparedStatement marca = c.prepareStatement("INSERT INTO procesados(evento_id) VALUES (?) ON CONFLICT DO NOTHING")) {
                marca.setString(1, eventoId);
                if (marca.executeUpdate() == 0) {
                    c.rollback();
                    return false;
                }
            }
            try (PreparedStatement efecto = c.prepareStatement("INSERT INTO efectos(pedido_id, tipo, evento_id) VALUES (?, ?, ?)")) {
                efecto.setString(1, pedidoId);
                efecto.setString(2, tipo);
                efecto.setString(3, eventoId);
                efecto.executeUpdate();
            }
            c.commit();
            return true;
        }
    }
}
