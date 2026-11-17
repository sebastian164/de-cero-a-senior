package consumidores;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;

/**
 * Consumidor de los pasos 3 a 7, como proceso aparte para poder matarlo de verdad. Es un ADAPTADOR de entrada: hace poll, traduce el registro a un
 * Pago, llama al puerto ProcesarPago y confirma cuando el puerto retorna. El negocio (ProcesadorEnArchivo) no conoce Kafka. Los modos de fallo
 * (muerte antes o después de confirmar, trabajo asíncrono) son banderas del adaptador, no del negocio.
 * Argumentos: modo tema grupo archivoDeProcesados [marcaDeFallo]
 * Modos:
 *   simple    lee y procesa, confirma después; apagado ordenado con SIGTERM (wakeup + close). Sin fin.
 *   despues   confirma DESPUÉS de procesar; al procesar el mensaje "FALLA" por primera vez, muere de golpe sin confirmar.
 *   antes     confirma ANTES de procesar; muere de golpe justo después de confirmar el mensaje "FALLA".
 *   asincrono confirmación automática y procesamiento en un grupo de hilos (300 ms por mensaje); sin fin.
 * Los modos despues y antes terminan solos cuando llevan 4 s sin recibir nada.
 */
public final class Consumidor {

    public static void main(String[] args) throws Exception {
        String modo = args[0], tema = args[1], grupo = args[2];
        Path procesados = Path.of(args[3]);
        Path marca = args.length > 4 ? Path.of(args[4]) : null;
        ProcesarPago puerto = new ProcesadorEnArchivo(procesados);
        Properties p = comun.Kf.consumo(comun.Kf.bootstrap(), grupo, "read_uncommitted");
        p.put("auto.offset.reset", "earliest");
        if (modo.equals("asincrono")) {
            p.put("enable.auto.commit", "true");
            p.put("auto.commit.interval.ms", "200");
        }
        p.put("max.poll.records", modo.equals("asincrono") ? "500" : "10");
        if (!modo.equals("simple")) {
            p.put("session.timeout.ms", "6000");          // una instancia muerta deja de bloquear el reparto a los 6 s
            p.put("heartbeat.interval.ms", "2000");
        }
        KafkaConsumer<String, String> c = new KafkaConsumer<>(p);
        if (modo.equals("simple")) {
            Thread principal = Thread.currentThread();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                c.wakeup();
                try {
                    principal.join(10_000);
                } catch (InterruptedException ignorada) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        c.subscribe(List.of(tema));
        ExecutorService hilos = Executors.newFixedThreadPool(4);
        long ultimoMensaje = System.currentTimeMillis();
        try {
            while (true) {
                ConsumerRecords<String, String> lote = c.poll(Duration.ofMillis(500));
                if (lote.isEmpty()) {
                    if (!modo.equals("simple") && !modo.equals("asincrono") && System.currentTimeMillis() - ultimoMensaje > 3000 && comun.Kf.alDia(c)) break;
                    continue;
                }
                ultimoMensaje = System.currentTimeMillis();
                for (ConsumerRecord<String, String> r : lote) {
                    switch (modo) {
                        case "despues" -> {
                            puerto.procesar(traducir(r));
                            if (r.value().equals("FALLA") && !Files.exists(marca)) {
                                Files.writeString(marca, "ya murió una vez");
                                Runtime.getRuntime().halt(137);             // muere tras procesar y antes de confirmar
                            }
                            c.commitSync(java.util.Map.of(new org.apache.kafka.common.TopicPartition(r.topic(), r.partition()),
                                    new org.apache.kafka.clients.consumer.OffsetAndMetadata(r.offset() + 1)));
                        }
                        case "antes" -> {
                            c.commitSync(java.util.Map.of(new org.apache.kafka.common.TopicPartition(r.topic(), r.partition()),
                                    new org.apache.kafka.clients.consumer.OffsetAndMetadata(r.offset() + 1)));
                            if (r.value().equals("FALLA") && !Files.exists(marca)) {
                                Files.writeString(marca, "ya murió una vez");
                                Runtime.getRuntime().halt(137);             // muere tras confirmar y antes de procesar
                            }
                            puerto.procesar(traducir(r));
                        }
                        case "asincrono" -> hilos.submit(() -> {
                            try {
                                Thread.sleep(300);                           // trabajo lento fuera del hilo de poll
                                puerto.procesar(traducir(r));
                            } catch (Exception e) {
                                throw new IllegalStateException(e);
                            }
                        });
                        default -> {
                            puerto.procesar(traducir(r));
                            c.commitSync();
                        }
                    }
                }
            }
        } catch (WakeupException apagado) {
            // apagado ordenado: se sale del bucle y se cierra
        } finally {
            if (!modo.equals("asincrono")) c.close();
            hilos.shutdownNow();
        }
    }

    static ProcesarPago.Pago traducir(ConsumerRecord<String, String> r) {
        return new ProcesarPago.Pago(r.partition(), r.offset(), r.value());
    }
}
