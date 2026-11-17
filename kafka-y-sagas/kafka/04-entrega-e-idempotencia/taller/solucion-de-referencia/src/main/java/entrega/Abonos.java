package entrega;

import comun.Kf;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Consumidor del tema abonos, como proceso aparte. Argumentos: modo grupo muereCada
 *   ingenuo       suma sin más; confirma después de procesar
 *   separadas     idempotente PERO con el saldo y la marca en dos transacciones distintas (paso 3)
 *   idempotente   saldo y marca de mensaje procesado en la MISMA transacción (paso 2)
 *   estado        el mensaje lleva el saldo resultante y una versión: UPDATE ... WHERE version &lt; ? (paso 4)
 *   fragil        idempotente, sin tratar los mensajes defectuosos: la excepción mata el proceso (paso 5)
 *   robusto       idempotente + reintento con espera creciente (fallos pasajeros) + tema de fallidos (envenenados)
 * muereCada=N: el proceso muere de golpe (halt) tras procesar el N-ésimo mensaje y antes de confirmar la posición.
 * Termina solo tras 4 s sin mensajes.
 */
public final class Abonos {

    /** Mensaje envenenado: no se arregla reintentando. */
    static final class Envenenado extends RuntimeException {
        private static final long serialVersionUID = 1L;
        Envenenado(String m) { super(m); }
    }

    static Connection conectar() throws SQLException {
        Connection c = DriverManager.getConnection(Kf.pgUrl(), "taller", "taller");
        c.setAutoCommit(false);
        try (var st = c.createStatement()) {
            st.execute("SET lock_timeout = '1s'");
        }
        c.commit();
        return c;
    }

    public static void main(String[] args) throws Exception {
        String modo = args[0], grupo = args[1];
        int muereCada = Integer.parseInt(args[2]);
        Properties p = Kf.consumo(Kf.bootstrap(), grupo, "read_uncommitted");
        p.put("max.poll.records", "20");
        p.put("session.timeout.ms", "6000");          // una instancia muerta deja de bloquear el reparto a los 6 s
        p.put("heartbeat.interval.ms", "2000");
        KafkaConsumer<String, String> c = new KafkaConsumer<>(p);
        KafkaProducer<String, String> fallidos = new KafkaProducer<>(Map.of("bootstrap.servers", Kf.bootstrap(),
                "key.serializer", StringSerializer.class.getName(), "value.serializer", StringSerializer.class.getName(), "acks", "all"));
        c.subscribe(List.of("abonos"));
        Connection db = conectar();
        int procesadosEnEstaVida = 0;
        long ultimo = System.currentTimeMillis(), inicio = ultimo;
        while (System.currentTimeMillis() - inicio < 120_000 && !(System.currentTimeMillis() - ultimo > 3000 && Kf.alDia(c))) {
            var lote = c.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> r : lote) {
                ultimo = System.currentTimeMillis();
                procesar(modo, db, c, fallidos, r, ++procesadosEnEstaVida, muereCada);
                if (muereCada > 0 && procesadosEnEstaVida % muereCada == 0 && !modo.equals("separadas")) {
                    Runtime.getRuntime().halt(137);                      // procesado, NO confirmado
                }
                c.commitSync(Map.of(new TopicPartition(r.topic(), r.partition()), new OffsetAndMetadata(r.offset() + 1)));
            }
        }
        c.close();
        fallidos.close();
        db.close();
    }

    static void procesar(String modo, Connection db, KafkaConsumer<String, String> c, KafkaProducer<String, String> fallidos,
                         ConsumerRecord<String, String> r, int n, int muereCada) throws Exception {
        switch (modo) {
            case "ingenuo" -> sumar(db, r.key(), monto(r.value()));
            case "separadas" -> {
                if (!yaProcesado(db, id(r.value()))) {
                    sumar(db, r.key(), monto(r.value()));              // transacción 1 confirmada
                    if (muereCada > 0 && n % muereCada == 0) Runtime.getRuntime().halt(137);   // caída ENTRE las dos escrituras
                    marcar(db, id(r.value()));                          // transacción 2
                }
            }
            case "idempotente", "fragil" -> idempotente(db, r);
            case "estado" -> aplicarEstado(db, r);
            case "robusto" -> robusto(db, fallidos, r);
            default -> throw new IllegalArgumentException(modo);
        }
    }

    // valor normal: "id;monto"; con estado: "id;saldo;version"
    static String id(String v) { return v.split(";")[0]; }

    static long monto(String v) {
        String[] t = v.split(";");
        if (t.length != 2) throw new Envenenado("formato inválido: " + v);
        try {
            return Long.parseLong(t[1]);
        } catch (NumberFormatException e) {
            throw new Envenenado("monto ilegible: " + v);
        }
    }

    static void sumar(Connection db, String cuenta, long monto) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement("UPDATE saldos SET saldo = saldo + ? WHERE cuenta = ?")) {
            ps.setLong(1, monto);
            ps.setString(2, cuenta);
            if (ps.executeUpdate() != 1) {
                db.rollback();
                throw new Envenenado("la cuenta " + cuenta + " no existe");
            }
        }
        db.commit();
    }

    static boolean yaProcesado(Connection db, String id) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement("SELECT 1 FROM procesados WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                boolean si = rs.next();
                db.commit();
                return si;
            }
        }
    }

    static void marcar(Connection db, String id) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement("INSERT INTO procesados(id) VALUES (?) ON CONFLICT DO NOTHING")) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
        db.commit();
    }

    /** El saldo y la marca en la misma transacción: o están los dos o no está ninguno. */
    static void idempotente(Connection db, ConsumerRecord<String, String> r) throws SQLException {
        long monto = monto(r.value());                       // falla antes de abrir nada si está mal formado
        try (PreparedStatement marca = db.prepareStatement("INSERT INTO procesados(id) VALUES (?) ON CONFLICT DO NOTHING")) {
            marca.setString(1, id(r.value()));
            if (marca.executeUpdate() == 0) {                // ya visto: no se repite el efecto
                db.commit();
                return;
            }
        }
        try (PreparedStatement ps = db.prepareStatement("UPDATE saldos SET saldo = saldo + ? WHERE cuenta = ?")) {
            ps.setLong(1, monto);
            ps.setString(2, r.key());
            if (ps.executeUpdate() != 1) {
                db.rollback();
                throw new Envenenado("la cuenta " + r.key() + " no existe");
            }
        }
        db.commit();
    }

    static void aplicarEstado(Connection db, ConsumerRecord<String, String> r) throws SQLException {
        String[] t = r.value().split(";");
        try (PreparedStatement ps = db.prepareStatement("UPDATE saldos SET saldo = ?, version = ? WHERE cuenta = ? AND version < ?")) {
            ps.setLong(1, Long.parseLong(t[1]));
            ps.setLong(2, Long.parseLong(t[2]));
            ps.setString(3, r.key());
            ps.setLong(4, Long.parseLong(t[2]));
            ps.executeUpdate();                              // 0 filas si ya estaba o si llega una versión vieja: se ignora
        }
        db.commit();
    }

    static boolean pasajero(SQLException e) {
        String s = e.getSQLState() == null ? "" : e.getSQLState();
        return s.equals("55P03") || s.startsWith("08") || s.startsWith("40") || s.equals("57014") || e instanceof java.sql.SQLTransientException;
    }

    /** Pasajero (la base no responde): reintentar con espera creciente, sin mandar nada a fallidos. Envenenado: a fallidos. */
    static void robusto(Connection db, KafkaProducer<String, String> fallidos, ConsumerRecord<String, String> r) throws Exception {
        long espera = 500;
        for (int intento = 1; ; intento++) {
            try {
                idempotente(db, r);
                return;
            } catch (Envenenado e) {
                db.rollback();
                var f = new ProducerRecord<>("abonos.fallidos", r.key(), r.value());
                f.headers().add("motivo", e.getMessage().getBytes(StandardCharsets.UTF_8));
                f.headers().add("tema-origen", r.topic().getBytes(StandardCharsets.UTF_8));
                f.headers().add("particion-origen", String.valueOf(r.partition()).getBytes(StandardCharsets.UTF_8));
                f.headers().add("posicion-origen", String.valueOf(r.offset()).getBytes(StandardCharsets.UTF_8));
                fallidos.send(f).get();
                return;
            } catch (SQLException e) {
                db.rollback();
                if (!pasajero(e)) throw e;
                System.out.println("reintento " + intento + " en " + espera + " ms: " + e.getSQLState());
                Thread.sleep(espera);
                espera = Math.min(espera * 2, 8000);
            }
        }
    }
}
