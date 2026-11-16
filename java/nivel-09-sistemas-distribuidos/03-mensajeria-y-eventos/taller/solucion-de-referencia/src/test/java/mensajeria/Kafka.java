package mensajeria;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import soporte.Entorno;

/** Ayudantes sobre kafka-clients 4.x contra el Kafka de compose.yml (puerto 43092). */
final class Kafka {

    private Kafka() { }

    static String tema(String prefijo, int particiones) {
        String nombre = prefijo + "-" + UUID.randomUUID().toString().substring(0, 8);
        try (Admin admin = Admin.create(Map.<String, Object>of("bootstrap.servers", Entorno.KAFKA))) {
            admin.createTopics(List.of(new NewTopic(nombre, particiones, (short) 1))).all().get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return nombre;
    }

    static KafkaProducer<String, String> productor(Map<String, Object> extra) {
        Properties p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, Entorno.KAFKA);
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.ACKS_CONFIG, "all");
        p.putAll(extra);
        return new KafkaProducer<>(p);
    }

    static KafkaConsumer<String, String> consumidor(String grupo) {
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, Entorno.KAFKA);
        p.put(ConsumerConfig.GROUP_ID_CONFIG, grupo);
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new KafkaConsumer<>(p);
    }

    /** Sondea hasta reunir {@code n} mensajes o agotar el plazo. */
    static List<ConsumerRecord<String, String>> leer(KafkaConsumer<String, String> c, int n, Duration plazo) {
        List<ConsumerRecord<String, String>> r = new ArrayList<>();
        long fin = System.nanoTime() + plazo.toNanos();
        while (r.size() < n && System.nanoTime() < fin) {
            c.poll(Duration.ofMillis(300)).forEach(r::add);
        }
        return r;
    }
}
