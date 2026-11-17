package comun;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

/** Utilidades de los talleres de Kafka: temas, lectura completa, conteos y procesos hijos. */
public final class Kf {

    private Kf() {
    }

    public static String bootstrap() {
        return System.getProperty("kafka.bootstrap", System.getenv().getOrDefault("KAFKA_BOOTSTRAP", "localhost:47092"));
    }

    static {
        java.sql.DriverManager.setLoginTimeout(15);          // una conexión no puede quedarse esperando para siempre
    }

    public static String pgUrl() {
        return System.getProperty("pg.url", System.getenv().getOrDefault("PG_URL", "jdbc:postgresql://localhost:47432/taller"));
    }

    public static Admin admin(String bootstrap) {
        return Admin.create(Map.of("bootstrap.servers", bootstrap, "request.timeout.ms", "15000"));
    }

    /** Crea el tema desde cero: si existía lo borra y espera a que desaparezca. */
    public static void tema(String bootstrap, String nombre, int particiones, int replicas, Map<String, String> config) {
        try (Admin admin = admin(bootstrap)) {
            if (admin.listTopics().names().get().contains(nombre)) {
                admin.deleteTopics(List.of(nombre)).all().get();
                for (int i = 0; i < 60 && admin.listTopics().names().get().contains(nombre); i++) {
                    Thread.sleep(500);
                }
            }
            for (int intento = 0; ; intento++) {
                try {
                    admin.createTopics(List.of(new NewTopic(nombre, particiones, (short) replicas).configs(config))).all().get();
                    return;
                } catch (java.util.concurrent.ExecutionException e) {
                    if (intento > 20) throw e;                 // el borrado puede tardar un poco en liberar el nombre
                    Thread.sleep(500);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("no se pudo crear " + nombre, e);
        }
    }

    public static void tema(String nombre, int particiones) {
        tema(bootstrap(), nombre, particiones, 1, Map.of());
    }

    public static Properties consumo(String bootstrap, String grupo, String aislamiento) {
        Properties p = new Properties();
        p.put("bootstrap.servers", bootstrap);
        if (grupo != null) p.put("group.id", grupo);
        p.put("key.deserializer", StringDeserializer.class.getName());
        p.put("value.deserializer", StringDeserializer.class.getName());
        p.put("enable.auto.commit", "false");
        p.put("auto.offset.reset", "earliest");
        p.put("isolation.level", aislamiento);
        return p;
    }

    /** Lee todo el tema, sin grupo, hasta la posición final que había al empezar. */
    public static List<ConsumerRecord<String, String>> leerTodo(String bootstrap, String tema, String aislamiento) {
        List<ConsumerRecord<String, String>> todo = new ArrayList<>();
        try (KafkaConsumer<String, String> c = new KafkaConsumer<>(consumo(bootstrap, null, aislamiento))) {
            List<TopicPartition> tps = new ArrayList<>();
            c.partitionsFor(tema).forEach(p -> tps.add(new TopicPartition(tema, p.partition())));
            c.assign(tps);
            c.seekToBeginning(tps);
            Map<TopicPartition, Long> fin = c.endOffsets(tps);
            long limite = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < limite && tps.stream().anyMatch(tp -> c.position(tp) < fin.get(tp))) {
                c.poll(Duration.ofMillis(300)).forEach(todo::add);
            }
        }
        return todo;
    }

    public static List<ConsumerRecord<String, String>> leerTodo(String tema) {
        return leerTodo(bootstrap(), tema, "read_uncommitted");
    }

    /** Mensajes del tema vistos por un lector read_committed. */
    public static List<ConsumerRecord<String, String>> leerConfirmados(String bootstrap, String tema) {
        return leerTodo(bootstrap, tema, "read_committed");
    }

    /** Suma de las posiciones finales menos las iniciales: sirve cuando no hay compactación ni transacciones. */
    public static long contar(String bootstrap, String tema) {
        try (KafkaConsumer<String, String> c = new KafkaConsumer<>(consumo(bootstrap, null, "read_uncommitted"))) {
            List<TopicPartition> tps = new ArrayList<>();
            c.partitionsFor(tema).forEach(p -> tps.add(new TopicPartition(tema, p.partition())));
            Map<TopicPartition, Long> fin = c.endOffsets(tps), ini = c.beginningOffsets(tps);
            return tps.stream().mapToLong(tp -> fin.get(tp) - ini.get(tp)).sum();
        }
    }

    public static long contar(String tema) {
        return contar(bootstrap(), tema);
    }

    /** El consumidor tiene partición asignada y ha leído hasta el final de todas. */
    public static boolean alDia(KafkaConsumer<?, ?> c) {
        var asignadas = c.assignment();
        if (asignadas.isEmpty()) return false;
        Map<TopicPartition, Long> fin = c.endOffsets(asignadas);
        return asignadas.stream().allMatch(tp -> c.position(tp) >= fin.get(tp));
    }

    // ---- procesos hijos y Docker ----

    public static Process hijo(Class<?> clase, String... args) {
        return hijo(clase, Map.of(), args);
    }

    public static Process hijo(Class<?> clase, Map<String, String> sistema, String... args) {
        List<String> orden = new ArrayList<>();
        orden.add(System.getProperty("java.home") + "/bin/java");
        orden.add("-Dkafka.bootstrap=" + Kf.bootstrap());
        orden.add("-Dpg.url=" + Kf.pgUrl());
        orden.add("-Dorg.slf4j.simpleLogger.defaultLogLevel=warn");
        sistema.forEach((k, v) -> orden.add("-D" + k + "=" + v));
        orden.add("-cp");
        orden.add(System.getProperty("java.class.path"));
        orden.add(clase.getName());
        orden.addAll(List.of(args));
        try {
            return new ProcessBuilder(orden).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.PIPE).start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Lee la salida de un hijo en un hilo aparte y la acumula (para no bloquearlo). */
    public static StringBuffer recoger(Process p) {
        StringBuffer sb = new StringBuffer();
        Thread t = new Thread(() -> {
            try (var r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                String l;
                while ((l = r.readLine()) != null) sb.append(l).append('\n');
            } catch (IOException ignorada) {
                // el hijo murió
            }
        });
        t.setDaemon(true);
        t.start();
        return sb;
    }

    public static String docker(String... args) {
        List<String> orden = new ArrayList<>(List.of("docker"));
        orden.addAll(List.of(args));
        try {
            Process p = new ProcessBuilder(orden).redirectErrorStream(true).start();
            String salida = new String(p.getInputStream().readAllBytes());
            p.waitFor(120, TimeUnit.SECONDS);
            return salida;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @FunctionalInterface
    public interface Condicion {
        boolean cumple() throws Exception;
    }

    public static void esperar(Condicion condicion, long milis, String que) {
        long limite = System.currentTimeMillis() + milis;
        var hilo = java.util.concurrent.Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "esperar");
            t.setDaemon(true);
            return t;
        });
        try {
            while (System.currentTimeMillis() < limite) {
                var intento = hilo.submit(() -> condicion.cumple());
                try {
                    // la propia condición también tiene límite: una consulta colgada no puede alargar la espera
                    if (intento.get(Math.max(1, limite - System.currentTimeMillis()), TimeUnit.MILLISECONDS)) return;
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                } catch (java.util.concurrent.TimeoutException e) {
                    intento.cancel(true);
                } catch (java.util.concurrent.ExecutionException ignorada) {
                    Thread.sleep(200);                           // la condición aún no puede evaluarse
                }
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            hilo.shutdownNow();
        }
        throw new AssertionError("tiempo agotado esperando: " + que);
    }

    public static String unico(String prefijo) {
        return prefijo + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static Map<String, String> mapa(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }
}
