package tienda.eventos;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** Lee los eventos pendientes y los publica en Kafka. Entrega al menos una vez: el consumidor es idempotente. */
@Component
public class RelevoOutbox {
    public static final String TEMA = "tienda.eventos";

    private final JdbcClient jdbc;
    private final KafkaTemplate<String, String> kafka;

    public RelevoOutbox(JdbcClient jdbc, KafkaTemplate<String, String> kafka) { this.jdbc = jdbc; this.kafka = kafka; }

    @Scheduled(fixedDelay = 200)
    @Transactional
    public int publicarPendientes() {
        record Fila(long id, String tipo, String clave, String carga) {}
        List<Fila> filas = jdbc.sql("SELECT id, tipo, clave, carga FROM eventos_outbox WHERE NOT publicado ORDER BY id LIMIT 50 FOR UPDATE SKIP LOCKED")
                .query((rs, n) -> new Fila(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4))).list();
        for (Fila f : filas) {
            try {
                // Formato: id|tipo|carga. El id del evento viaja con el mensaje para que el consumidor pueda deduplicar.
                kafka.send(TEMA, f.clave(), f.id() + "|" + f.tipo() + "|" + f.carga()).get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException("No se pudo publicar el evento " + f.id() + "; se reintenta en la próxima vuelta", e);
            }
            jdbc.sql("UPDATE eventos_outbox SET publicado = true WHERE id = ?").param(f.id()).update();
        }
        return filas.size();
    }
}
