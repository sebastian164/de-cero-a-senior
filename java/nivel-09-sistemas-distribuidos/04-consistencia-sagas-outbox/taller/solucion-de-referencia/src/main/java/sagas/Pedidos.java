package sagas;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Consumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import soporte.Entorno;

/** Guardar un pedido y publicar su evento: la doble escritura y la solución con Outbox. */
public final class Pedidos {

    public static final String ESQUEMA = """
        CREATE TABLE IF NOT EXISTS pedidos (id BIGINT PRIMARY KEY, total BIGINT NOT NULL);
        CREATE TABLE IF NOT EXISTS outbox (
            id            UUID PRIMARY KEY,
            tipo          VARCHAR(100) NOT NULL,
            agregado_id   VARCHAR(100) NOT NULL,
            contenido     JSONB        NOT NULL,
            creado_en     TIMESTAMPTZ  NOT NULL DEFAULT now(),
            publicado_en  TIMESTAMPTZ
        );
        """;

    private final String url;
    private final String bootstrap;
    private final String tema;

    public Pedidos(String url, String bootstrap, String tema) {
        this.url = url;
        this.bootstrap = bootstrap;
        this.tema = tema;
    }

    KafkaProducer<String, String> productor() {
        Properties p = new Properties();
        p.put("bootstrap.servers", bootstrap);
        p.put("key.serializer", StringSerializer.class.getName());
        p.put("value.serializer", StringSerializer.class.getName());
        p.put("acks", "all");
        return new KafkaProducer<>(p);
    }

    static String json(long pedidoId, long total) {
        return "{\"pedidoId\":" + pedidoId + ",\"total\":" + total + "}";
    }

    /** Doble escritura: base de datos y luego broker, sin transacción común. {@code entreMedias} corre entre las dos. */
    public void crearConDobleEscritura(long id, long total, Runnable entreMedias) throws Exception {
        try (Connection c = Entorno.conectar(url); PreparedStatement ps = c.prepareStatement("INSERT INTO pedidos VALUES (?, ?)")) {
            ps.setLong(1, id);
            ps.setLong(2, total);
            ps.executeUpdate();                                          // repositorio.guardar(pedido)
        }
        entreMedias.run();                                               // aquí cae el proceso
        try (var p = productor()) {
            p.send(new ProducerRecord<>(tema, String.valueOf(id), json(id, total))).get();   // kafka.send(...)
        }
    }

    /** Outbox: pedido y evento en la MISMA transacción. */
    public void crearConOutbox(long id, long total) throws SQLException {
        try (Connection c = Entorno.conectar(url)) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO pedidos VALUES (?, ?)")) {
                ps.setLong(1, id);
                ps.setLong(2, total);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO outbox (id, tipo, agregado_id, contenido) VALUES (?, 'PedidoCreado', ?, ?::jsonb)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setString(2, String.valueOf(id));
                ps.setString(3, json(id, total));
                ps.executeUpdate();
            }
            c.commit();
        }
    }

    /**
     * Publicador: lee lo pendiente, lo envía y lo marca. {@code despuesDeEnviar} corre tras el envío y antes de marcar.
     * Devuelve cuántos eventos envió.
     */
    public int publicarPendientes(Consumer<String> despuesDeEnviar) throws Exception {
        int n = 0;
        try (Connection c = Entorno.conectar(url); var p = productor()) {
            c.setAutoCommit(false);
            List<String[]> pendientes = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, agregado_id, contenido::text FROM outbox WHERE publicado_en IS NULL ORDER BY creado_en FOR UPDATE SKIP LOCKED");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    pendientes.add(new String[]{rs.getString(1), rs.getString(2), rs.getString(3)});
                }
            }
            for (String[] e : pendientes) {
                var registro = new ProducerRecord<>(tema, e[1], e[2]);
                registro.headers().add("idEvento", e[0].getBytes());
                p.send(registro).get();
                n++;
                despuesDeEnviar.accept(e[0]);                            // si cae aquí: enviado pero sin marcar
                try (PreparedStatement ps = c.prepareStatement("UPDATE outbox SET publicado_en = now() WHERE id = ?::uuid")) {
                    ps.setString(1, e[0]);
                    ps.executeUpdate();
                }
                c.commit();
            }
        }
        return n;
    }

    /** Punto de entrada de un proceso hijo al que la prueba mata con SIGKILL. */
    public static void main(String[] args) throws Exception {
        String modo = args[0];
        Pedidos pedidos = new Pedidos(args[1], args[2], args[3]);
        long id = Long.parseLong(args[4]);
        Runnable esperarLaMuerte = () -> {
            System.out.println("GUARDADO");
            System.out.flush();
            try { Thread.sleep(600_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        };
        if (modo.equals("doble")) {
            pedidos.crearConDobleEscritura(id, 100, esperarLaMuerte);
        } else {
            pedidos.crearConOutbox(id, 100);
            esperarLaMuerte.run();
        }
    }
}
