package entrega;

import comun.Kf;
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
 * Paso 9: lectura, transformación y escritura entre dos temas con transacciones de Kafka.
 * Argumentos: muereEn (número de mensaje tras el cual muere dentro de una transacción abierta; 0 = no muere).
 * Termina tras 4 s sin mensajes.
 */
public final class Canalizacion {

    public static void main(String[] args) throws Exception {
        int muereEn = Integer.parseInt(args[0]);
        Properties pc = Kf.consumo(Kf.bootstrap(), "canal", "read_committed");
        pc.put("max.poll.records", "10");
        pc.put("session.timeout.ms", "6000");
        pc.put("heartbeat.interval.ms", "2000");
        KafkaConsumer<String, String> c = new KafkaConsumer<>(pc);
        KafkaProducer<String, String> p = new KafkaProducer<>(Map.of("bootstrap.servers", Kf.bootstrap(),
                "key.serializer", StringSerializer.class.getName(), "value.serializer", StringSerializer.class.getName(),
                "transactional.id", "canal-1", "enable.idempotence", "true", "acks", "all"));
        p.initTransactions();                                          // expulsa a la instancia anterior y aborta su transacción
        c.subscribe(List.of("entrada-t"));
        long ultimo = System.currentTimeMillis(), inicio = ultimo;
        int vistos = 0;
        while (System.currentTimeMillis() - inicio < 120_000 && !(System.currentTimeMillis() - ultimo > 3000 && Kf.alDia(c))) {
            var lote = c.poll(Duration.ofMillis(500));
            if (lote.isEmpty()) continue;
            ultimo = System.currentTimeMillis();
            p.beginTransaction();
            Map<TopicPartition, OffsetAndMetadata> posiciones = new java.util.HashMap<>();
            for (ConsumerRecord<String, String> r : lote) {
                p.send(new ProducerRecord<>("salida-t", r.key(), r.value().toUpperCase()));
                posiciones.put(new TopicPartition(r.topic(), r.partition()), new OffsetAndMetadata(r.offset() + 1));
                if (muereEn > 0 && ++vistos == muereEn) {
                    p.flush();                                         // lo escrito ya está en el tema, sin confirmar
                    Runtime.getRuntime().halt(137);
                }
            }
            p.sendOffsetsToTransaction(posiciones, c.groupMetadata());  // la posición se confirma EN la transacción
            p.commitTransaction();
        }
        c.close();
        p.close();
    }
}
