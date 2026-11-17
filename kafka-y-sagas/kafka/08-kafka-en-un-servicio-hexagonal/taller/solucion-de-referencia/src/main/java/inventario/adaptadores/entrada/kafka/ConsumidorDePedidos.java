package inventario.adaptadores.entrada.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inventario.adaptadores.salida.jdbc.InventarioJdbc.BaseNoDisponible;
import inventario.aplicacion.ReservarPorPedido;
import inventario.aplicacion.ReservarPorPedido.Orden;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Adaptador de entrada: traduce el mensaje a un Orden, llama al puerto y, cuando el puerto retorna, confirma la posición.
 * Política de errores (decisión del adaptador, no del caso de uso):
 *  - mensaje ilegible o que incumple el contrato -> tema de fallidos con el motivo y el origen, y se sigue;
 *  - la base no responde (BaseNoDisponible)      -> se reintenta con espera creciente, sin confirmar y sin mandar a fallidos;
 *  - cualquier otra excepción                    -> se propaga: el proceso se detiene (no se descarta un error desconocido).
 */
public final class ConsumidorDePedidos implements AutoCloseable {

    public static final String TEMA = "pedidos.creados";
    public static final String FALLIDOS = "pedidos.creados.fallidos";

    private final ReservarPorPedido puerto;
    private final ObjectMapper json = new ObjectMapper();
    private final AtomicBoolean seguir = new AtomicBoolean(true);
    private final KafkaConsumer<String, String> consumidor;
    private final KafkaProducer<String, String> fallidos;
    private Thread hilo;
    /** Solo para las pruebas: muere de golpe tras procesar el n-ésimo mensaje y antes de confirmar la posición. */
    public volatile int morirTrasProcesar = 0;
    private int procesados;

    public ConsumidorDePedidos(String bootstrap, String grupo, ReservarPorPedido puerto) {
        this.puerto = puerto;
        Properties p = new Properties();
        p.put("bootstrap.servers", bootstrap);
        p.put("group.id", grupo);
        p.put("key.deserializer", StringDeserializer.class.getName());
        p.put("value.deserializer", StringDeserializer.class.getName());
        p.put("enable.auto.commit", "false");
        p.put("auto.offset.reset", "earliest");
        p.put("session.timeout.ms", "6000");
        p.put("heartbeat.interval.ms", "2000");
        consumidor = new KafkaConsumer<>(p);
        fallidos = new KafkaProducer<>(Map.of("bootstrap.servers", bootstrap, "key.serializer", StringSerializer.class.getName(),
                "value.serializer", StringSerializer.class.getName(), "acks", "all"));
    }

    /** Lector tolerante: lee solo lo que usa e ignora lo demás (por eso un campo nuevo no rompe nada). */
    Orden leer(String valor) {
        try {
            JsonNode n = json.readTree(valor);
            JsonNode d = n.get("datos");
            if (n.get("id") == null || d == null || d.get("pedidoId") == null || d.get("producto") == null || d.get("cantidad") == null) {
                throw new MensajeInvalido("faltan campos del contrato");
            }
            return new Orden(n.get("id").asText(), d.get("pedidoId").asText(), d.get("producto").asText(), d.get("cantidad").asInt());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new MensajeInvalido("JSON ilegible");
        }
    }

    static final class MensajeInvalido extends RuntimeException {
        private static final long serialVersionUID = 1L;

        MensajeInvalido(String motivo) {
            super(motivo);
        }
    }

    public void arrancar() {
        consumidor.subscribe(List.of(TEMA));
        hilo = new Thread(this::bucle);
        hilo.start();
    }

    public void bucle() {
        try {
            while (seguir.get()) {
                for (ConsumerRecord<String, String> r : consumidor.poll(Duration.ofMillis(300))) {
                    tratar(r);
                    if (morirTrasProcesar > 0 && ++procesados == morirTrasProcesar) Runtime.getRuntime().halt(137);
                    consumidor.commitSync(Map.of(new TopicPartition(r.topic(), r.partition()), new OffsetAndMetadata(r.offset() + 1)));
                }
            }
        } catch (org.apache.kafka.common.errors.WakeupException apagado) {
            // apagado ordenado
        }
    }

    private void tratar(ConsumerRecord<String, String> r) {
        long espera = 300;
        while (seguir.get()) {
            try {
                puerto.reservar(leer(r.value()));
                return;
            } catch (MensajeInvalido e) {
                var f = new ProducerRecord<>(FALLIDOS, r.key(), r.value());
                f.headers().add("motivo", e.getMessage().getBytes(StandardCharsets.UTF_8));
                f.headers().add("origen", (r.topic() + "-" + r.partition() + "@" + r.offset()).getBytes(StandardCharsets.UTF_8));
                try {
                    fallidos.send(f).get();
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
                return;
            } catch (BaseNoDisponible e) {
                try { Thread.sleep(espera); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
                espera = Math.min(espera * 2, 4000);
            }
        }
    }

    @Override
    public void close() throws InterruptedException {
        seguir.set(false);
        consumidor.wakeup();
        if (hilo != null) hilo.join(15_000);
        consumidor.close();
        fallidos.close();
    }
}
