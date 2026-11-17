package tienda;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/** La tabla de salida y su publicador: nada cruza de la base a Kafka sin pasar por aquí (sesión 07 de Kafka). */
public class Salida {

    /** El simulacro lo apaga para "matar el publicador" sin matar el servicio. */
    public volatile boolean activa = true;
    /** Punto de fallo para las pruebas: si es n > 0, el proceso muere tras publicar la n-ésima fila y ANTES de marcarla como publicada. */
    public volatile long muereAlPublicar;
    private final java.util.concurrent.atomic.AtomicLong publicadas = new java.util.concurrent.atomic.AtomicLong();
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final KafkaTemplate<String, String> kafka;

    public Salida(JdbcTemplate jdbc, TransactionTemplate tx, KafkaTemplate<String, String> kafka) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.kafka = kafka;
    }

    /** Se llama dentro de la transacción del cambio de estado. */
    public static void poner(JdbcTemplate jdbc, String tema, String clave, String payload) {
        jdbc.update("INSERT INTO salida(id, tema, clave, payload) VALUES (?, ?, ?, ?)", UUID.randomUUID(), tema, clave, payload);
    }

    @Scheduled(fixedDelay = 100)
    public void publicar() {
        if (!activa) return;
        try {
            tx.executeWithoutResult(estado -> {
                List<Map<String, Object>> filas = jdbc.queryForList("SELECT s.id, s.tema, s.clave, s.payload FROM salida s WHERE s.publicada IS NULL "
                        + "AND NOT EXISTS (SELECT 1 FROM salida a WHERE a.clave = s.clave AND a.seq < s.seq AND a.publicada IS NULL) "
                        + "ORDER BY s.seq LIMIT 100 FOR UPDATE OF s SKIP LOCKED");
                for (Map<String, Object> f : filas) {
                    try {
                        kafka.send((String) f.get("tema"), (String) f.get("clave"), (String) f.get("payload")).get(5, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (Exception e) {
                        return;                                      // el broker no responde: lo que queda espera; el orden se conserva
                    }
                    if (muereAlPublicar > 0 && publicadas.incrementAndGet() == muereAlPublicar) Runtime.getRuntime().halt(137);
                    jdbc.update("UPDATE salida SET publicada = clock_timestamp() WHERE id = ?", f.get("id"));
                }
            });
        } catch (RuntimeException ignorada) {
            // la base o el broker se están reiniciando: se reintenta en la siguiente vuelta
        }
    }

    public long pendientes() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM salida WHERE publicada IS NULL", Long.class);
        return n == null ? 0 : n;
    }
}
