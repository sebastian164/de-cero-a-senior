package tienda.integrador.plataforma;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * La tabla de salida y su publicador (sesión 07 de Kafka): nada cruza de la base a Kafka sin pasar por aquí. Es infraestructura común
 * de plataforma, no de ningún servicio: cada servicio escribe en SU tabla (su esquema) con poner() desde su adaptador JDBC.
 */
public class PublicadorDeSalida {

    /** Se apaga para "detener el publicador" sin matar el servicio. */
    public volatile boolean activa = true;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final KafkaTemplate<String, String> kafka;
    private final long muereAlPublicar;
    private final AtomicLong publicados = new AtomicLong();

    /** muereAlPublicar: si es n > 0, el proceso muere justo después de que Kafka confirme el n-ésimo mensaje y ANTES de marcar la fila. */
    public PublicadorDeSalida(JdbcTemplate jdbc, TransactionTemplate tx, KafkaTemplate<String, String> kafka, long muereAlPublicar) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.kafka = kafka;
        this.muereAlPublicar = muereAlPublicar;
    }

    /** Se llama dentro de la transacción del cambio de estado. */
    public static void poner(JdbcTemplate jdbc, String tema, String clave, String payload) {
        jdbc.update("INSERT INTO salida(id, tema, clave, payload) VALUES (?, ?, ?, ?)", UUID.randomUUID(), tema, clave, payload);
    }

    public static void crearTabla(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS salida(id uuid PRIMARY KEY, seq bigserial UNIQUE, tema text NOT NULL, clave text NOT NULL, payload text NOT NULL, "
                + "creada timestamptz DEFAULT clock_timestamp(), publicada timestamptz)");
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
                        kafka.send((String) f.get("tema"), (String) f.get("clave"), (String) f.get("payload")).get(5, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        return;                                      // el broker no responde: lo que queda espera; el orden se conserva
                    }
                    if (muereAlPublicar > 0 && publicados.incrementAndGet() == muereAlPublicar) Runtime.getRuntime().halt(137);   // publicado, sin marcar
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
