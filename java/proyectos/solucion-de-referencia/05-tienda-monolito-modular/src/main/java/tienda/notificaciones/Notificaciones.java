package tienda.notificaciones;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Módulo notificaciones: reacciona a eventos. Idempotente: el mismo evento entregado dos veces notifica una vez. */
@Component
public class Notificaciones {
    private final JdbcClient jdbc;

    public Notificaciones(JdbcClient jdbc) { this.jdbc = jdbc; }

    @KafkaListener(topics = "tienda.eventos", groupId = "notificaciones")
    @Transactional
    public void alRecibir(String mensaje) {
        String[] p = mensaje.split("\\|", 3);
        long eventoId = Long.parseLong(p[0]);
        int nuevo = jdbc.sql("INSERT INTO notificaciones_procesadas (evento_id) VALUES (?) ON CONFLICT DO NOTHING").param(eventoId).update();
        if (nuevo == 0) return; // ya procesado
        long pedidoId = Long.parseLong(p[2]);
        String texto = p[1].equals("PedidoConfirmado") ? "Tu pedido " + pedidoId + " fue confirmado" : "Tu pedido " + pedidoId + " no se pudo completar";
        jdbc.sql("INSERT INTO notificaciones_enviadas (evento_id, pedido_id, mensaje) VALUES (?,?,?)").params(eventoId, pedidoId, texto).update();
    }

    public long enviadas(long pedidoId) {
        return jdbc.sql("SELECT count(*) FROM notificaciones_enviadas WHERE pedido_id = ?").param(pedidoId).query(Long.class).single();
    }
}
