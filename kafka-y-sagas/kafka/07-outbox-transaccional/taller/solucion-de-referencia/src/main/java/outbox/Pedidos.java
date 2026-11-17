package outbox;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

/** El servicio de Pedidos, con las dos formas de publicar. */
public final class Pedidos {

    public static final String TEMA = "pedidos.eventos";

    private Pedidos() {
    }

    static String payload(String eventoId, String tipo, String pedidoId) {
        return "{\"id\":\"" + eventoId + "\",\"tipo\":\"" + tipo + "\",\"version\":1,\"datos\":{\"pedidoId\":\"" + pedidoId + "\"}}";
    }

    // ---------------------------------------------------------------- el "antes": doble escritura

    /** Guardar y luego publicar. Si publicar falla o el proceso muere en medio, el pedido queda sin evento. */
    public static void crearConDobleEscritura(String id, KafkaProducer<String, String> productor, boolean morirEntreLasDos) throws Exception {
        try (Connection c = Bd.conectar()) {
            insertarPedido(c, id);
            c.commit();                                                     // 1. confirmado en la base
        }
        if (morirEntreLasDos) Runtime.getRuntime().halt(137);                // el proceso muere aquí
        productor.send(new ProducerRecord<>(TEMA, id, payload(UUID.randomUUID().toString(), "PedidoCreado", id))).get(5, TimeUnit.SECONDS);   // 2.
    }

    /** "Publiquemos primero": si la base rechaza, el evento ya salió. */
    public static void crearPublicandoPrimero(String id, KafkaProducer<String, String> productor, boolean baseRechaza) throws Exception {
        productor.send(new ProducerRecord<>(TEMA, id, payload(UUID.randomUUID().toString(), "PedidoCreado", id))).get(5, TimeUnit.SECONDS);
        try (Connection c = Bd.conectar()) {
            insertarPedido(c, id, baseRechaza ? null : "cliente-1");     // cliente null: la base rechaza la transacción
            c.commit();
        }
    }

    // ---------------------------------------------------------------- el "después": outbox

    /** Pedido y evento en la misma transacción: o los dos o ninguno. */
    public static void crearConOutbox(String id, String cliente, boolean fallarAlFinal) throws SQLException {
        try (Connection c = Bd.conectar()) {
            try {
                insertarPedido(c, id, cliente);
                escribirEvento(c, id, "PedidoCreado");
                if (fallarAlFinal) throw new IllegalStateException("una regla de negocio no se cumplió");
                c.commit();
            } catch (RuntimeException | SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** Un cambio de estado del pedido, con su evento, en una transacción. */
    public static void cambiarEstado(String id, String estado, String tipoEvento) throws SQLException {
        try (Connection c = Bd.conectar()) {
            try (PreparedStatement ps = c.prepareStatement("UPDATE pedidos SET estado = ? WHERE id = ?")) {
                ps.setString(1, estado);
                ps.setString(2, id);
                ps.executeUpdate();
            }
            escribirEvento(c, id, tipoEvento);
            c.commit();
        }
    }

    static void insertarPedido(Connection c, String id) throws SQLException {
        insertarPedido(c, id, "cliente-1");
    }

    static void insertarPedido(Connection c, String id, String cliente) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO pedidos(id, cliente, estado) VALUES (?, ?, 'CREADO')")) {
            ps.setString(1, id);
            ps.setString(2, cliente);
            ps.executeUpdate();
        }
    }

    static void escribirEvento(Connection c, String pedidoId, String tipo) throws SQLException {
        UUID eventoId = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO salida(id, agregado, tipo, payload) VALUES (?, ?, ?, ?)")) {
            ps.setObject(1, eventoId);
            ps.setString(2, pedidoId);
            ps.setString(3, tipo);
            ps.setString(4, payload(eventoId.toString(), tipo, pedidoId));
            ps.executeUpdate();
        }
    }
}
