package outbox;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * El publicador por sondeo. Cada vuelta: una transacción que reclama filas pendientes, las publica EN ORDEN y las marca.
 *
 * Reclamo "ingenuo" (para mostrar el fallo): SELECT ... WHERE publicada IS NULL ORDER BY seq LIMIT n, sin bloqueo.
 * Reclamo correcto: FOR UPDATE SKIP LOCKED (dos instancias no toman las mismas filas) y NOT EXISTS (una fila no sale mientras
 * haya otra anterior del mismo agregado sin publicar: el orden por agregado sobrevive a fallos y a varias instancias).
 */
public final class Publicador implements AutoCloseable {

    public enum Reclamo { INGENUO, CORRECTO }

    private final KafkaProducer<String, String> productor;
    private final Reclamo reclamo;
    private final int lote;
    private final boolean seguirTrasUnFallo;
    /** Fallo inyectado: la publicación de este tipo de evento falla mientras valga true. */
    public volatile java.util.function.Predicate<String> falla = tipo -> false;
    /** Muere de golpe justo después de publicar y antes de marcar (paso 6). */
    public volatile boolean morirAntesDeMarcar;
    public final AtomicLong publicadas = new AtomicLong();
    public final AtomicLong consultas = new AtomicLong();
    private final AtomicBoolean seguir = new AtomicBoolean(true);
    private Thread hilo;

    public Publicador(String bootstrap, Reclamo reclamo, int lote, boolean seguirTrasUnFallo) {
        this.productor = new KafkaProducer<>(Map.of("bootstrap.servers", bootstrap, "key.serializer", StringSerializer.class.getName(),
                "value.serializer", StringSerializer.class.getName(), "acks", "all", "enable.idempotence", "true",
                "max.block.ms", "3000", "delivery.timeout.ms", "6000", "request.timeout.ms", "3000"));
        this.reclamo = reclamo;
        this.lote = lote;
        this.seguirTrasUnFallo = seguirTrasUnFallo;
    }

    /** Una vuelta. Devuelve cuántas filas publicó y marcó. */
    public int unaVuelta() throws SQLException {
        consultas.incrementAndGet();
        try (Connection c = Bd.conectar()) {
            String sql = reclamo == Reclamo.INGENUO
                    ? "SELECT id, agregado, tipo, payload FROM salida WHERE publicada IS NULL ORDER BY seq LIMIT ?"
                    : "SELECT s.id, s.agregado, s.tipo, s.payload FROM salida s WHERE s.publicada IS NULL "
                    + "AND NOT EXISTS (SELECT 1 FROM salida a WHERE a.agregado = s.agregado AND a.seq < s.seq AND a.publicada IS NULL) "
                    + "ORDER BY s.seq LIMIT ? FOR UPDATE OF s SKIP LOCKED";
            List<Object[]> filas = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, lote);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) filas.add(new Object[] {rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4)});
                }
            }
            List<UUID> publicadasEnEstaVuelta = new ArrayList<>();
            for (Object[] f : filas) {
                try {
                    if (falla.test((String) f[2])) throw new IllegalStateException("fallo inyectado al publicar " + f[2]);
                    productor.send(new ProducerRecord<>(Pedidos.TEMA, (String) f[1], (String) f[3])).get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    if (seguirTrasUnFallo) continue;           // el error de orden: saltar la fila que falló y seguir con las siguientes
                    break;                                      // lo correcto: parar; lo siguiente espera
                }
                publicadasEnEstaVuelta.add((UUID) f[0]);
            }
            if (morirAntesDeMarcar && !publicadasEnEstaVuelta.isEmpty()) {
                Runtime.getRuntime().halt(137);                 // publicado, sin marcar, transacción sin confirmar
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE salida SET publicada = clock_timestamp() WHERE id = ?")) {
                for (UUID id : publicadasEnEstaVuelta) {
                    ps.setObject(1, id);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            c.commit();
            publicadas.addAndGet(publicadasEnEstaVuelta.size());
            return publicadasEnEstaVuelta.size();
        }
    }

    public void arrancar(long intervaloMs) {
        hilo = new Thread(() -> {
            while (seguir.get()) {
                try {
                    if (unaVuelta() == 0) Thread.sleep(intervaloMs);
                } catch (InterruptedException e) {
                    return;
                } catch (Exception e) {
                    try { Thread.sleep(intervaloMs); } catch (InterruptedException ie) { return; }
                }
            }
        });
        hilo.setDaemon(true);
        hilo.start();
    }

    @Override
    public void close() throws InterruptedException {
        seguir.set(false);
        if (hilo != null) {
            hilo.interrupt();
            hilo.join(10_000);
        }
        productor.close(java.time.Duration.ofSeconds(2));
    }

    // ---------------------------------------------------------------- métricas del paso 10

    /** Filas pendientes y edad en segundos de la más antigua. */
    public static double[] pendientes() throws SQLException {
        try (Connection c = Bd.conectar(); var st = c.createStatement();
             var rs = st.executeQuery("SELECT count(*), coalesce(extract(epoch FROM clock_timestamp() - min(creada)), 0) FROM salida WHERE publicada IS NULL")) {
            rs.next();
            return new double[] {rs.getLong(1), rs.getDouble(2)};
        }
    }

    /** Limpieza: borra las ya publicadas hace más de la retención. Devuelve cuántas. */
    public static int limpiar(int retencionSegundos) throws SQLException {
        try (Connection c = Bd.conectar(); PreparedStatement ps = c.prepareStatement("DELETE FROM salida WHERE publicada IS NOT NULL AND publicada < clock_timestamp() - make_interval(secs => ?)")) {
            ps.setInt(1, retencionSegundos);
            int n = ps.executeUpdate();
            c.commit();
            return n;
        }
    }
}
