package consumidores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import comun.Ev;
import comun.Kf;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConsumidoresTest {

    static final String B = Kf.bootstrap();

    static KafkaProducer<String, String> productor() {
        return new KafkaProducer<>(Map.of("bootstrap.servers", B, "key.serializer", StringSerializer.class.getName(),
                "value.serializer", StringSerializer.class.getName(), "acks", "all"));
    }

    static void producir(String tema, String clave, String... valores) throws Exception {
        try (var p = productor()) {
            for (String v : valores) p.send(new ProducerRecord<>(tema, clave, v));
            p.flush();
        }
    }

    /** Asignación actual del grupo: nombre del miembro -> número de particiones. */
    static Map<String, Integer> asignacion(String grupo) throws Exception {
        try (Admin a = Kf.admin(B)) {
            var d = a.describeConsumerGroups(List.of(grupo)).all().get().get(grupo);
            return d.members().stream().collect(Collectors.toMap(m -> m.consumerId(), m -> m.assignment().topicPartitions().size()));
        }
    }

    static long retraso(String grupo, String tema) throws Exception {
        try (Admin a = Kf.admin(B); KafkaConsumer<String, String> c = new KafkaConsumer<>(Kf.consumo(B, null, "read_uncommitted"))) {
            Map<TopicPartition, OffsetAndMetadata> conf = a.listConsumerGroupOffsets(grupo).partitionsToOffsetAndMetadata().get();
            List<TopicPartition> tps = c.partitionsFor(tema).stream().map(p -> new TopicPartition(tema, p.partition())).toList();
            Map<TopicPartition, Long> fin = c.endOffsets(tps);
            return tps.stream().mapToLong(tp -> fin.get(tp) - (conf.containsKey(tp) ? conf.get(tp).offset() : 0)).sum();
        }
    }

    /** Un consumidor en un hilo, de un grupo; procesa cada mensaje con la pausa indicada y confirma después. */
    static final class Hilo implements AutoCloseable {
        final AtomicBoolean seguir = new AtomicBoolean(true);
        final AtomicLong procesados = new AtomicLong();
        final Thread hilo;
        Hilo(String grupo, String tema, long pausaMs) {
            hilo = new Thread(() -> {
                try (var c = new KafkaConsumer<String, String>(Kf.consumo(B, grupo, "read_uncommitted"))) {
                    c.subscribe(List.of(tema));
                    while (seguir.get()) {
                        for (ConsumerRecord<String, String> r : c.poll(Duration.ofMillis(200))) {
                            if (pausaMs > 0) { try { Thread.sleep(pausaMs); } catch (InterruptedException e) { return; } }
                            procesados.incrementAndGet();
                        }
                        c.commitSync();
                    }
                }
            });
            hilo.setDaemon(true);
            hilo.start();
        }
        @Override public void close() throws InterruptedException { seguir.set(false); hilo.join(10_000); }
    }

    // ------------------------------------------------------------------ pasos 1 y 2
    @Test
    @Order(1)
    void paso2_repartoDeParticionesAlEntrarConsumidores() throws Exception {
        Kf.tema("taller3", 3);
        List<Hilo> hilos = new ArrayList<>();
        try {
            for (int n = 1; n <= 4; n++) {
                hilos.add(new Hilo("taller", "taller3", 0));
                int esperados = n;
                List<Integer> reparto = new ArrayList<>();
                for (int i = 0; i < esperados; i++) reparto.add(3 / esperados + (i < 3 % esperados ? 1 : 0));    // 3 particiones lo más parejas posible
                java.util.Collections.sort(reparto);
                Kf.esperar(() -> asignacion("taller").values().stream().sorted().toList().equals(reparto), 90_000, "reparto estable con " + esperados + " consumidores");
                Ev.nota("2. con " + n + " consumidor(es): particiones por miembro " + asignacion("taller").values().stream().sorted().toList());
            }
            assertEquals(List.of(0, 1, 1, 1), asignacion("taller").values().stream().sorted().toList(), "el cuarto no recibe nada: hay tres particiones");
        } finally {
            for (Hilo h : hilos) h.close();
        }
    }

    // ------------------------------------------------------------------ paso 3
    @Test
    @Order(2)
    void paso3_matarContraApagadoOrdenado() throws Exception {
        Kf.tema("reparto", 3);
        Path log = Files.createTempFile("reparto", ".log");
        Process a = Kf.hijo(Consumidor.class, "simple", "reparto", "g3", log.toString());
        Process b = Kf.hijo(Consumidor.class, "simple", "reparto", "g3", log.toString());
        Kf.recoger(a); Kf.recoger(b);
        try {
            Kf.esperar(() -> { var m = asignacion("g3"); return m.size() == 2 && m.values().stream().mapToInt(i -> i).sum() == 3; }, 60_000, "dos miembros");
            long t0 = System.currentTimeMillis();
            a.destroyForcibly();                                       // SIGKILL: no avisa al grupo
            Kf.esperar(() -> { var m = asignacion("g3"); return m.size() == 1 && m.values().stream().mapToInt(i -> i).sum() == 3; }, 120_000, "reparto tras SIGKILL");
            long matar = System.currentTimeMillis() - t0;
            Process a2 = Kf.hijo(Consumidor.class, "simple", "reparto", "g3", log.toString());
            Kf.recoger(a2);
            Kf.esperar(() -> { var m = asignacion("g3"); return m.size() == 2 && m.values().stream().mapToInt(i -> i).sum() == 3; }, 60_000, "dos miembros otra vez");
            t0 = System.currentTimeMillis();
            a2.destroy();                                              // SIGTERM: el gancho hace wakeup() y close()
            Kf.esperar(() -> { var m = asignacion("g3"); return m.size() == 1 && m.values().stream().mapToInt(i -> i).sum() == 3; }, 120_000, "reparto tras SIGTERM");
            long ordenado = System.currentTimeMillis() - t0;
            Ev.nota("3. reparto de las particiones del consumidor que se fue: SIGKILL " + matar + " ms; apagado ordenado " + ordenado + " ms");
            assertTrue(matar > 10_000, "al matar, el grupo espera a session.timeout.ms (45 s por defecto)");
            assertTrue(ordenado < matar / 3, "al cerrar, el consumidor avisa que se va y el reparto es casi inmediato");
        } finally {
            a.destroyForcibly(); b.destroyForcibly();
        }
    }

    // ------------------------------------------------------------------ paso 4
    @Test
    @Order(3)
    void paso4_otroGrupoEarliestYLatest() throws Exception {
        Kf.tema("historial", 1);
        producir("historial", "k", "h1", "h2", "h3", "h4", "h5");
        for (String reset : new String[] {"earliest", "latest"}) {
            var props = Kf.consumo(B, "grupo-" + reset, "read_uncommitted");
            props.put("auto.offset.reset", reset);
            try (var c = new KafkaConsumer<String, String>(props)) {
                c.subscribe(List.of("historial"));
                int primeros = 0;
                long limite = System.currentTimeMillis() + 6000;
                while (System.currentTimeMillis() < limite) primeros += c.poll(Duration.ofMillis(300)).count();
                producir("historial", "k", "nuevo-" + reset);
                int despues = 0;
                limite = System.currentTimeMillis() + 6000;
                while (System.currentTimeMillis() < limite && despues < 1) despues += c.poll(Duration.ofMillis(300)).count();
                Ev.nota("4. auto.offset.reset=" + reset + ": recibió " + primeros + " del historial y " + despues + " posteriores");
                assertEquals(reset.equals("earliest") ? 5 + 0 : 0, primeros + (reset.equals("earliest") ? 0 : 0) - (reset.equals("earliest") ? 0 : 0) , "historial visto");
            }
        }
    }

    // ------------------------------------------------------------------ pasos 5 y 6
    @Test
    @Order(4)
    void paso5_confirmarDespuesDeProcesarRepite() throws Exception {
        long[] r = ejecutarConFallo("despues", "t5");
        Ev.nota("5. confirmar después: producidos 10, procesados (con repeticiones) " + r[0] + ", FALLA procesado " + r[1] + " veces, retraso final " + r[2]);
        assertEquals(2, r[1], "la repetición: FALLA se procesó dos veces");
        assertEquals(11, r[0], "10 mensajes + 1 repetición");
        assertEquals(0, r[2]);
    }

    @Test
    @Order(5)
    void paso6_confirmarAntesDeProcesarPierde() throws Exception {
        long[] r = ejecutarConFallo("antes", "t6");
        Ev.nota("6. confirmar antes: producidos 10, procesados " + r[0] + ", FALLA procesado " + r[1] + " veces, retraso final del grupo " + r[2]);
        assertEquals(0, r[1], "la pérdida: FALLA se confirmó y nunca se procesó");
        assertEquals(9, r[0]);
        assertEquals(0, r[2], "el retraso es 0: nada delata la pérdida en las métricas del grupo");
    }

    /** Devuelve {líneas procesadas, veces que se procesó FALLA, retraso final}. */
    private long[] ejecutarConFallo(String modo, String tema) throws Exception {
        Kf.tema(tema, 1);
        producir(tema, "k", "m1", "m2", "m3", "m4", "FALLA", "m6", "m7", "m8", "m9", "m10");
        Path log = Files.createTempFile(tema, ".log"); Files.deleteIfExists(log);
        Path marca = Files.createTempFile("marca", ".txt"); Files.deleteIfExists(marca);
        Process uno = Kf.hijo(Consumidor.class, modo, tema, "g-" + tema, log.toString(), marca.toString());
        Kf.recoger(uno);
        assertTrue(uno.waitFor(60, TimeUnit.SECONDS));
        assertEquals(137, uno.exitValue(), "murió de golpe");
        Process dos = Kf.hijo(Consumidor.class, modo, tema, "g-" + tema, log.toString(), marca.toString());
        Kf.recoger(dos);
        assertTrue(dos.waitFor(60, TimeUnit.SECONDS));
        List<String> lineas = Files.readAllLines(log);
        return new long[] {lineas.size(), lineas.stream().filter(l -> l.endsWith(":FALLA")).count(), retraso("g-" + tema, tema)};
    }

    // ------------------------------------------------------------------ paso 7
    @Test
    @Order(6)
    void paso7_procesamientoAsincronoConAutoconfirmacionPierde() throws Exception {
        Kf.tema("t7", 1);
        String[] valores = new String[200];
        for (int i = 0; i < 200; i++) valores[i] = "v" + i;
        producir("t7", "k", valores);
        Path log = Files.createTempFile("t7", ".log"); Files.deleteIfExists(log);
        Process p = Kf.hijo(Consumidor.class, "asincrono", "t7", "g-t7", log.toString());
        Kf.recoger(p);
        Kf.esperar(() -> Files.exists(log) && Files.readAllLines(log).size() >= 10, 60_000, "algún trabajo hecho");
        Thread.sleep(700);
        p.destroyForcibly();                                              // trabajo pendiente en la cola del grupo de hilos
        p.waitFor(10, TimeUnit.SECONDS);
        long hechos = Files.readAllLines(log).stream().map(l -> l.split(":")[2]).distinct().count();
        long retraso = retraso("g-t7", "t7");
        Ev.nota("7. autoconfirmación + hilos, proceso muerto: producidos 200, procesados " + hechos + ", retraso del grupo " + retraso + " -> perdidos " + (200 - hechos - retraso));
        assertTrue(hechos < 200 - retraso, "hay mensajes que el grupo da por leídos y nadie procesó");
    }

    // ------------------------------------------------------------------ paso 8
    /** Procesar tarda más que max.poll.interval.ms. Devuelve {entregas de LENTO, rebalanceos, confirmaciones fallidas}. */
    private long[] escenarioLento(String tema, long maxPollMs, boolean conPausa) throws Exception {
        Kf.tema(tema, 1);
        producir(tema, "k", "a", "LENTO", "c");
        var props = Kf.consumo(B, "g-" + tema, "read_uncommitted");
        props.put("max.poll.interval.ms", String.valueOf(maxPollMs));
        props.put("session.timeout.ms", "10000");
        AtomicInteger entregas = new AtomicInteger(), rebalanceos = new AtomicInteger(), fallidas = new AtomicInteger();
        AtomicLong vistosC = new AtomicLong();
        AtomicBoolean cerrando = new AtomicBoolean();
        ExecutorService trabajador = Executors.newSingleThreadExecutor();
        long fin = System.currentTimeMillis() + 28_000;
        try (var c = new KafkaConsumer<String, String>(props)) {
            c.subscribe(List.of(tema), new ConsumerRebalanceListener() {
                @Override public void onPartitionsRevoked(Collection<TopicPartition> p) { if (!p.isEmpty() && !cerrando.get()) rebalanceos.incrementAndGet(); }
                @Override public void onPartitionsAssigned(Collection<TopicPartition> p) { }
            });
            CompletableFuture<Void> trabajo = null;
            long siguiente = 0;
            while (System.currentTimeMillis() < fin && vistosC.get() < 3) {
                ConsumerRecords<String, String> lote = c.poll(Duration.ofMillis(300));
                if (conPausa && trabajo != null) {
                    if (trabajo.isDone()) {                                       // el trabajador terminó: se confirma desde el hilo de poll
                        c.resume(c.assignment()); c.commitSync(Map.of(new TopicPartition(tema, 0), new OffsetAndMetadata(siguiente)));
                        vistosC.set(siguiente); trabajo = null;
                    }
                }
                for (ConsumerRecord<String, String> r : lote) {
                    if (r.value().equals("LENTO")) entregas.incrementAndGet();
                    long dur = r.value().equals("LENTO") ? 8000 : 10;
                    if (conPausa) {
                        c.pause(c.assignment());                                   // sigue haciendo poll (vacío) mientras el trabajador procesa
                        siguiente = r.offset() + 1;
                        trabajo = CompletableFuture.runAsync(() -> { try { Thread.sleep(dur); } catch (InterruptedException e) { throw new IllegalStateException(e); } }, trabajador);
                        c.seek(new TopicPartition(tema, 0), r.offset() + 1);       // el lote restante se vuelve a leer tras reanudar
                        break;
                    }
                    try { Thread.sleep(dur); } catch (InterruptedException e) { throw new IllegalStateException(e); }
                    try {
                        c.commitSync(Map.of(new TopicPartition(tema, 0), new OffsetAndMetadata(r.offset() + 1)));
                        vistosC.set(r.offset() + 1);
                    } catch (org.apache.kafka.clients.consumer.CommitFailedException e) {
                        fallidas.incrementAndGet();
                        break;
                    }
                }
            }
            cerrando.set(true);
        } finally {
            trabajador.shutdownNow();
        }
        return new long[] {entregas.get(), rebalanceos.get(), fallidas.get(), vistosC.get()};
    }

    @Test
    @Order(7)
    void paso8_procesarMasQueMaxPollInterval() throws Exception {
        long[] ingenuo = escenarioLento("lento1", 4000, false);
        Ev.nota("8. ingenuo (max.poll.interval.ms=4 s, procesar 8 s): LENTO entregado " + ingenuo[0] + " veces, rebalanceos " + ingenuo[1] + ", confirmaciones rechazadas " + ingenuo[2] + ", posición final " + ingenuo[3]);
        assertTrue(ingenuo[0] >= 2 && ingenuo[1] >= 1 && ingenuo[2] >= 1, "el grupo expulsa al consumidor y el mensaje vuelve a entregarse");
        long[] subir = escenarioLento("lento2", 15_000, false);
        Ev.nota("8. solución A, subir max.poll.interval.ms a 15 s: LENTO entregado " + subir[0] + " vez, rebalanceos " + subir[1] + ", confirmaciones rechazadas " + subir[2] + ", posición final " + subir[3]);
        assertEquals(1, subir[0]); assertEquals(0, subir[1]); assertEquals(0, subir[2]); assertEquals(3, subir[3]);
        long[] pausa = escenarioLento("lento3", 4000, true);
        Ev.nota("8. solución B, trabajador aparte + pause()/resume(): LENTO entregado " + pausa[0] + " vez, rebalanceos " + pausa[1] + ", confirmaciones rechazadas " + pausa[2] + ", posición final " + pausa[3]);
        assertEquals(1, pausa[0]); assertEquals(0, pausa[1]); assertEquals(0, pausa[2]); assertEquals(3, pausa[3]);
    }

    // ------------------------------------------------------------------ paso 9
    @Test
    @Order(8)
    void paso9_retrasoYPonerseAlDia() throws Exception {
        long[] segundos = new long[3];
        for (int n = 1; n <= 3; n++) {
            String tema = "retraso" + n;
            Kf.tema(tema, 3);
            AtomicBoolean produciendo = new AtomicBoolean(true);
            Thread prod = new Thread(() -> {
                try (var p = productor()) {
                    long i = 0, t0 = System.nanoTime();
                    while (produciendo.get()) {
                        p.send(new ProducerRecord<>(tema, "k" + (i % 30), "v" + i));
                        i++;
                        long espera = t0 + i * 10_000_000L - System.nanoTime();       // 100 mensajes por segundo
                        if (espera > 0) { try { Thread.sleep(espera / 1_000_000, (int) (espera % 1_000_000)); } catch (InterruptedException e) { return; } }
                    }
                }
            });
            prod.start();
            Thread.sleep(10_000);                                                      // los consumidores "detenidos": 10 s de retraso acumulado
            long inicial = retraso("g-ret" + n, tema);
            List<Hilo> hilos = new ArrayList<>();
            long t0 = System.currentTimeMillis();
            for (int i = 0; i < n; i++) hilos.add(new Hilo("g-ret" + n, tema, 5));    // 5 ms por mensaje
            try {
                final int num = n;
                Kf.esperar(() -> retraso("g-ret" + num, tema) < 30, 120_000, "ponerse al día");
            } finally {
                segundos[n - 1] = (System.currentTimeMillis() - t0) / 1000;
                produciendo.set(false); prod.join();
                for (Hilo h : hilos) h.close();
            }
            Ev.nota("9. " + n + " consumidor(es): retraso inicial " + inicial + " mensajes, al día en " + segundos[n - 1] + " s (con el productor activo a 100/s)");
        }
        assertTrue(segundos[2] <= segundos[0], "más consumidores, menos tiempo (hasta el número de particiones)");
    }

    // ------------------------------------------------------------------ paso 10
    @Test
    @Order(9)
    void paso10_moverLaPosicionDelGrupoAlPrincipio() throws Exception {
        Kf.tema("reinicio", 1);
        producir("reinicio", "k", "r1", "r2", "r3", "r4");
        try (var h = new Hilo("g-reinicio", "reinicio", 0)) {
            Kf.esperar(() -> retraso("g-reinicio", "reinicio") == 0 && h.procesados.get() == 4, 30_000, "consumir los cuatro");
        }
        String tool = "/opt/kafka/bin/kafka-consumer-groups.sh";
        String contenedor = Kf.docker("ps", "--filter", "name=pub-kafka-y-sagas-kafka-1", "--format", "{{.Names}}").trim().split("\n")[0];
        try (var h = new Hilo("g-reinicio", "reinicio", 0)) {
            Thread.sleep(2000);
            String con = Kf.docker("exec", contenedor, tool, "--bootstrap-server", "kafka:19092", "--group", "g-reinicio", "--topic", "reinicio", "--reset-offsets", "--to-earliest", "--execute");
            Ev.nota("10. con el consumidor activo: " + con.trim().replace("\n", " | "));
            assertTrue(con.contains("inactive") || con.contains("Error"), con);
        }
        Thread.sleep(1500);
        String sin = Kf.docker("exec", contenedor, tool, "--bootstrap-server", "kafka:19092", "--group", "g-reinicio", "--topic", "reinicio", "--reset-offsets", "--to-earliest", "--execute");
        Ev.nota("10. con el grupo detenido: " + sin.trim().replace("\n", " | "));
        assertTrue(sin.contains("reinicio") && !sin.contains("Error"), sin);
        assertEquals(4, retraso("g-reinicio", "reinicio"), "tras el reinicio, el retraso es todo el tema");
        try (var h = new Hilo("g-reinicio", "reinicio", 0)) {
            Kf.esperar(() -> h.procesados.get() >= 4, 30_000, "reprocesar");
            Ev.nota("10. al arrancar de nuevo se reprocesaron " + h.procesados.get() + " mensajes (por eso el consumidor debe ser idempotente)");
        }
    }
}
