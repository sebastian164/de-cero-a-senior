package productores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import comun.Ev;
import comun.Kf;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartitionInfo;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Taller Kafka 02. Parte A (pasos 1-5) y los pasos 10 con el clúster de tres brokers del curso
 * (propiedad kafka.cluster, ver README.md de esta carpeta). El resto, con el broker único.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProductoresTest {

    static final String CLUSTER = System.getProperty("kafka.cluster", "");
    static final String CONT = "pub-kafka-y-sagas-kc-kafka-%d-1";

    static String detener(int n) { return Kf.docker("stop", CONT.formatted(n)); }
    static String arrancar(int n) { return Kf.docker("start", CONT.formatted(n)); }

    static String describir(Admin admin, String tema) throws Exception {
        var d = admin.describeTopics(List.of(tema)).allTopicNames().get().get(tema);
        StringBuilder sb = new StringBuilder();
        for (TopicPartitionInfo p : d.partitions()) {
            sb.append("  partición %d líder %s réplicas %s isr %s%n".formatted(p.partition(),
                    p.leader() == null ? "ninguno" : p.leader().id(),
                    p.replicas().stream().map(n -> "" + n.id()).collect(Collectors.joining(",")),
                    p.isr().stream().map(n -> "" + n.id()).collect(Collectors.joining(","))));
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- Parte A (clúster)

    @Test
    @Order(1)
    @Timeout(value = 12, unit = TimeUnit.MINUTES)
    void parteA_pasos1a5_clusterDeTresBrokers() throws Exception {
        assumeTrue(!CLUSTER.isBlank(), "requiere el clúster (kafka.cluster)");
        try {
            Kf.tema(CLUSTER, "pa-tres", 3, 3, Map.of());
            try (Admin admin = Kf.admin(CLUSTER)) {
                Ev.nota("A1. tema pa-tres, 3 particiones, factor 3:\n" + describir(admin, "pa-tres"));
                assertTrue(admin.describeTopics(List.of("pa-tres")).allTopicNames().get().get("pa-tres").partitions()
                        .stream().allMatch(p -> p.replicas().size() == 3 && p.isr().size() == 3));

                detener(3);
                Kf.esperar(() -> sinNodo(admin, "pa-tres", 3), 60_000, "ISR sin el broker 3");
                Ev.nota("A2. con el broker 3 detenido:\n" + describir(admin, "pa-tres"));
                try (var p = new ProductorPedidos(CLUSTER, "pa-tres")) {
                    for (int i = 0; i < 30; i++) p.publicar(i, "cliente-" + (i % 3));
                    p.vaciar();
                    assertEquals(30, p.confirmados.get());
                }
                assertEquals(30, Kf.leerTodo(CLUSTER, "pa-tres", "read_uncommitted").size());
                Ev.nota("A2. con un broker caído: 30 producidos y 30 leídos (se puede producir y consumir)");
                arrancar(3);
                Kf.esperar(() -> admin.describeTopics(List.of("pa-tres")).allTopicNames().get().get("pa-tres").partitions().stream().allMatch(p -> p.isr().size() == 3), 90_000, "ISR completo");

                // Pasos 3 y 4. Los tres brokers del curso son también controladores (quórum de 2 de 3). Con DOS detenidos no hay
                // quórum de metadatos: nadie puede encoger el ISR ni asignar identificadores de productor idempotente nuevos.
                // Por eso el paso 3 se hace en dos escenarios, y todo lo que podría colgarse lleva espera acotada.
                // Escenario 1 (determinista, un solo broker caído): min.insync.replicas=3. El ISR baja a 2 < 3 y el broker rechaza.
                Kf.tema(CLUSTER, "pa-min3", 1, 3, Map.of("min.insync.replicas", "3"));
                int liderMin3 = admin.describeTopics(List.of("pa-min3")).allTopicNames().get().get("pa-min3").partitions().get(0).leader().id();
                int caido = java.util.stream.IntStream.rangeClosed(1, 3).filter(n -> n != liderMin3).findFirst().getAsInt();
                try (var p = new ProductorPedidos(CLUSTER, "pa-min3", "acks", "all")) {
                    p.publicar(0, "cliente-1"); p.vaciar();
                    assertEquals(1, p.confirmados.get(), "con los tres en el ISR, acks=all confirma");
                }
                detener(caido);
                Kf.esperar(() -> sinNodo(admin, "pa-min3", caido), 60_000, "ISR de pa-min3 sin el broker " + caido);
                Ev.nota("A3 (escenario 1). pa-min3 (min.insync.replicas=3), broker " + caido + " detenido:\n" + describir(admin, "pa-min3"));
                try (var p = new ProductorPedidos(CLUSTER, "pa-min3", "acks", "all", "enable.idempotence", "false", "retries", "0")) {
                    p.publicar(1, "cliente-1");
                    assertTrue(p.vaciarHasta(30_000), "el rechazo del broker llega rápido");
                    assertEquals(0, p.confirmados.get());
                    Ev.nota("A3 (escenario 1). el error exacto del broker (acks=all, retries=0): " + p.errores);
                    assertTrue(p.errores.get(0).contains("org.apache.kafka.common.errors.NotEnoughReplicasException"), p.errores.toString());
                }
                try (var p = new ProductorPedidos(CLUSTER, "pa-min3", "acks", "all", "delivery.timeout.ms", "10000", "request.timeout.ms", "5000")) {
                    p.publicar(1, "cliente-1");
                    assertTrue(p.vaciarHasta(30_000));
                    Ev.nota("A3 (escenario 1). con los valores por defecto (idempotente, reintentos): el error es reintentable, se insiste hasta que vence delivery.timeout.ms: " + p.errores);
                    assertEquals(0, p.confirmados.get());
                }
                try (var p = new ProductorPedidos(CLUSTER, "pa-min3", "acks", "1", "enable.idempotence", "false")) {
                    p.publicar(2, "cliente-1");
                    assertTrue(p.vaciarHasta(30_000));
                    Ev.nota("A4 (escenario 1). acks=1, mismo broker caído: confirmados=" + p.confirmados.get() + " " + p.registro
                            + " (el líder confirma solo, sin esperar a las réplicas: el riesgo que se acepta)");
                    assertEquals(1, p.confirmados.get());
                }
                arrancar(caido);
                Kf.esperar(() -> admin.describeTopics(List.of("pa-min3")).allTopicNames().get().get("pa-min3").partitions().get(0).isr().size() == 3, 120_000, "ISR de pa-min3 completo");

                // Escenario 2 (el literal del enunciado): min.insync.replicas=2 y se detienen los DOS seguidores.
                Kf.tema(CLUSTER, "pa-min2", 1, 3, Map.of("min.insync.replicas", "2"));
                int lider = admin.describeTopics(List.of("pa-min2")).allTopicNames().get().get("pa-min2").partitions().get(0).leader().id();
                int[] seguidores = java.util.stream.IntStream.rangeClosed(1, 3).filter(n -> n != lider).toArray();
                Ev.nota("A3 (escenario 2). pa-min2 (1 partición, min.insync.replicas=2): líder " + lider + ", seguidores " + seguidores[0] + " y " + seguidores[1]);
                // El productor idempotente se crea y confirma un envío ANTES del segundo fallo: ya tiene su identificador.
                var conIdentificador = new ProductorPedidos(CLUSTER, "pa-min2", "acks", "all", "delivery.timeout.ms", "15000", "request.timeout.ms", "5000");
                try {
                    detener(seguidores[0]);
                    Kf.esperar(() -> sinNodo(admin, "pa-min2", seguidores[0]), 60_000, "ISR sin el primer seguidor");
                    conIdentificador.publicar(1, "cliente-1");
                    assertTrue(conIdentificador.vaciarHasta(30_000));
                    assertEquals(1, conIdentificador.confirmados.get(), "con ISR=2 y acks=all se puede producir");
                    detener(seguidores[1]);
                    Thread.sleep(8_000);
                    Ev.nota("A3 (escenario 2). con dos brokers detenidos (sin quórum de controladores):\n" + describir(admin, "pa-min2"));
                    conIdentificador.publicar(2, "cliente-1");
                    boolean termino = conIdentificador.vaciarHasta(40_000);
                    Ev.nota("A3 (escenario 2). acks=all, dos brokers caídos, productor que ya tenía identificador (termino=" + termino + "): " + conIdentificador.errores);
                    assertEquals(1, conIdentificador.confirmados.get(), "el segundo mensaje no se confirma");
                    assertTrue(termino && conIdentificador.errores.size() == 1
                            && (conIdentificador.errores.get(0).contains("NotEnoughReplicasException") || conIdentificador.errores.get(0).contains("TimeoutException")), conIdentificador.errores.toString());
                } finally {
                    conIdentificador.productor.close(java.time.Duration.ofSeconds(5));
                }
                // Un productor idempotente NUEVO, sin quórum, ni siquiera consigue su identificador: espera acotada, nada de flush() sin límite
                try (var p = new ProductorPedidos(CLUSTER, "pa-min2", "acks", "all", "delivery.timeout.ms", "15000", "request.timeout.ms", "5000")) {
                    p.publicar(3, "cliente-1");
                    boolean termino = p.vaciarHasta(25_000);
                    Ev.nota("A3 (escenario 2). productor idempotente NUEVO sin quórum: termino en 25 s=" + termino + ", confirmados=" + p.confirmados.get() + " " + p.errores
                            + (termino ? "" : " (sigue esperando su identificador: el lote no caduca mientras no hay identificador)"));
                    assertEquals(0, p.confirmados.get());
                    p.productor.close(java.time.Duration.ofMillis(100));        // cierre inmediato: se descarta lo pendiente
                }
                // Sin idempotencia no hace falta identificador: el broker superviviente (el líder) responde
                try (var p = new ProductorPedidos(CLUSTER, "pa-min2", "acks", "all", "enable.idempotence", "false", "retries", "0", "request.timeout.ms", "8000", "delivery.timeout.ms", "10000")) {
                    p.publicar(4, "cliente-1");
                    assertTrue(p.vaciarHasta(30_000));
                    Ev.nota("A3 (escenario 2). sin idempotencia ni reintentos: " + p.errores);
                    assertEquals(0, p.confirmados.get());
                }
                // Paso 4: acks=1 en esta misma situación (el líder vivo confirma solo)
                try (var p = new ProductorPedidos(CLUSTER, "pa-min2", "acks", "1", "enable.idempotence", "false")) {
                    p.publicar(5, "cliente-1");
                    assertTrue(p.vaciarHasta(30_000));
                    Ev.nota("A4 (escenario 2). acks=1, mismo escenario: confirmados=" + p.confirmados.get() + " " + p.registro);
                    assertEquals(1, p.confirmados.get(), "acks=1 confirma con solo el líder: el riesgo que se acepta");
                }
                // Paso 5: se levantan los brokers y se observa cómo se recupera el ISR
                arrancar(seguidores[0]); arrancar(seguidores[1]);
                long t0 = System.currentTimeMillis();
                Kf.esperar(() -> admin.describeTopics(List.of("pa-min2")).allTopicNames().get().get("pa-min2").partitions().get(0).isr().size() == 3, 120_000, "ISR vuelve a tres");
                Ev.nota("A5. ISR de pa-min2 de vuelta a 3 tras " + (System.currentTimeMillis() - t0) + " ms de arrancar los brokers:\n" + describir(admin, "pa-min2"));
                try (var p = new ProductorPedidos(CLUSTER, "pa-min2", "acks", "all")) {
                    p.publicar(6, "cliente-1");
                    assertTrue(p.vaciarHasta(60_000));
                    assertEquals(1, p.confirmados.get(), "acks=all vuelve a funcionar");
                }
            }
        } finally {
            for (int n = 1; n <= 3; n++) arrancar(n);
        }
    }

    private static boolean sinNodo(Admin admin, String tema, int nodo) throws Exception {
        return admin.describeTopics(List.of(tema)).allTopicNames().get().get(tema).partitions().stream()
                .allMatch(p -> p.isr().stream().noneMatch(n -> n.id() == nodo) && p.leader() != null);
    }

    @Test
    @Order(2)
    void paso10_sinPerdidasNiDuplicadosAlCaerElLider() throws Exception {
        assumeTrue(!CLUSTER.isBlank(), "requiere el clúster (kafka.cluster)");
        try (Admin admin = Kf.admin(CLUSTER)) {
            for (int n = 1; n <= 3; n++) arrancar(n);
            Kf.esperar(() -> true, 1000, "");
            Kf.tema(CLUSTER, "pa-lider", 1, 3, Map.of("min.insync.replicas", "2"));
            Kf.esperar(() -> admin.describeTopics(List.of("pa-lider")).allTopicNames().get().get("pa-lider").partitions().get(0).isr().size() == 3, 60_000, "ISR");
            int lider = admin.describeTopics(List.of("pa-lider")).allTopicNames().get().get("pa-lider").partitions().get(0).leader().id();
            final int total = 20_000;
            try (var p = new ProductorPedidos(CLUSTER, "pa-lider", "acks", "all", "enable.idempotence", "true", "linger.ms", "5")) {
                Thread matar = new Thread(() -> {
                    Kf.esperar(() -> p.confirmados.get() > 3_000, 60_000, "3 000 envíos");
                    Ev.nota("10. se detiene el broker líder " + lider + " con " + p.confirmados.get() + " confirmados");
                    detener(lider);
                });
                matar.start();
                for (long i = 1; i <= total; i++) { p.publicar(i, "cliente-1"); if (i % 50 == 0) Thread.sleep(1); }
                p.vaciar();
                matar.join();
                Ev.nota("10. confirmados=" + p.confirmados.get() + " fallidos=" + p.fallidos.get() + " " + p.errores.stream().limit(2).toList());
                assertEquals(total, p.confirmados.get());
                assertEquals(0, p.fallidos.get());
            }
            List<ConsumerRecord<String, String>> todo = Kf.leerTodo(CLUSTER, "pa-lider", "read_uncommitted");
            Set<String> distintos = todo.stream().map(ConsumerRecord::value).collect(Collectors.toSet());
            Ev.nota("10. en el tema: " + todo.size() + " mensajes, " + distintos.size() + " distintos (duplicados: " + (todo.size() - distintos.size()) + ")");
            assertEquals(total, todo.size());
            assertEquals(total, distintos.size());
            List<Long> numeros = todo.stream().map(r -> Long.parseLong(r.value().replaceAll(".*\"p-(\\d+)\".*", "$1"))).toList();
            for (int i = 1; i < numeros.size(); i++) assertTrue(numeros.get(i) > numeros.get(i - 1), "el orden se conservó");
            Ev.nota("10. orden conservado: la posición n tiene el pedido n, sin huecos");
            for (int n = 1; n <= 3; n++) arrancar(n);
        }
    }

    // ---------------------------------------------------------------- Parte B (broker único)

    @Test
    @Order(3)
    void pasos6y7_publicaConClaveYRegistraParticionYPosicion() {
        Kf.tema("pedidos", 3);
        try (var p = new ProductorPedidos(Kf.bootstrap(), "pedidos")) {
            for (int i = 0; i < 12; i++) p.publicar(i, "cliente-" + (i % 3));
            p.vaciar();
            assertEquals(12, p.confirmados.get());
            p.registro.stream().limit(3).forEach(Ev::nota);
            assertTrue(p.registro.stream().allMatch(l -> l.matches("OK pedido p-\\d+ partición \\d posición \\d+")));
        }
        var lectura = Kf.leerTodo("pedidos");
        Map<String, Set<Integer>> particionesPorClave = new java.util.HashMap<>();
        lectura.forEach(r -> particionesPorClave.computeIfAbsent(r.key(), k -> new HashSet<>()).add(r.partition()));
        assertEquals(3, particionesPorClave.size());
        particionesPorClave.values().forEach(s -> assertEquals(1, s.size(), "una clave, una partición"));
        assertTrue(lectura.get(0).value().contains("\"tipo\":\"PedidoCreado\""));
    }

    @Test
    @Order(4)
    void paso8_lingerCeroContraLingerCincuenta() throws Exception {
        long[] rafaga = new long[2], sincrono = new long[2];
        String[] lingers = {"0", "50"};
        for (int k = 0; k < 2; k++) {
            Kf.tema("lingers", 3);
            try (var p = new ProductorPedidos(Kf.bootstrap(), "lingers", "linger.ms", lingers[k])) {
                p.publicar(0, "cliente-0"); p.vaciar();                       // calentamiento: conexiones y metadatos
                long t0 = System.nanoTime();
                for (int i = 1; i <= 1000; i++) p.publicar(i, "cliente-" + (i % 10));    // a) ráfaga asíncrona
                p.vaciar();
                rafaga[k] = (System.nanoTime() - t0) / 1_000_000;
                assertEquals(1001, p.confirmados.get());
                t0 = System.nanoTime();
                for (int i = 1; i <= 100; i++) {                                 // b) un mensaje a la vez, esperando cada confirmación
                    p.productor.send(new ProducerRecord<>("lingers", "cliente-1", ProductorPedidos.json(i, "cliente-1"))).get();
                }
                sincrono[k] = (System.nanoTime() - t0) / 1_000_000;
            }
            assertEquals(1101, Kf.contar("lingers"));
        }
        Ev.nota("8. ráfaga asíncrona de 1000: linger.ms=0 -> " + rafaga[0] + " ms; linger.ms=50 -> " + rafaga[1] + " ms");
        Ev.nota("8. 100 envíos síncronos (send().get()): linger.ms=0 -> " + sincrono[0] + " ms; linger.ms=50 -> " + sincrono[1] + " ms");
        assertTrue(sincrono[1] > sincrono[0] * 5, "esperar cada confirmación paga el linger entero en cada envío");
    }

    @Test
    @Order(5)
    void paso9_matarContraApagadoOrdenado() throws Exception {
        long[] perdidos = new long[2];
        String[] modos = {"matar", "ordenado"};
        for (int k = 0; k < 2; k++) {
            String tema = "lote-" + modos[k];
            Kf.tema(tema, 1);
            Path progreso = Files.createTempFile("progreso", ".txt");
            Process hijo = Kf.hijo(Lote.class, tema, "2000", modos[k], progreso.toString());
            Kf.recoger(hijo);
            Kf.esperar(() -> { try { return Files.readString(progreso).contains("listo"); } catch (Exception e) { return false; } }, 60_000, "2 000 envíos aceptados");
            if (k == 0) hijo.destroyForcibly(); else hijo.destroy();     // SIGKILL contra SIGTERM
            hijo.waitFor(30, TimeUnit.SECONDS);
            Thread.sleep(1000);
            long aceptados = Long.parseLong(Files.readString(progreso).trim().split(" ")[0]);
            long enTema = Kf.contar(tema);
            perdidos[k] = aceptados - enTema;
            Ev.nota("9. " + modos[k] + ": send() aceptó exactamente " + aceptados + ", en el tema hay " + enTema + " -> perdidos " + Math.max(0, perdidos[k]));
        }
        assertTrue(perdidos[0] > 0, "matar el proceso pierde lo que esperaba en el búfer (linger.ms=60000)");
        assertTrue(perdidos[1] <= 0, "el apagado ordenado cierra el productor y no pierde nada");
    }

    @Test
    @Order(6)
    void paso11_duplicadosSinIdempotencia() {
        long[] duplicados = new long[2];
        String[] idem = {"false", "true"};
        for (int k = 0; k < 2; k++) {
            String tema = "dups-" + idem[k];
            Kf.tema(tema, 1);
            // request.timeout.ms mínimo: la petición "caduca" en el cliente aunque el broker la esté escribiendo, y se reintenta
            try (var p = new ProductorPedidos(Kf.bootstrap(), tema, "enable.idempotence", idem[k], "acks", "all", "request.timeout.ms", "2", "delivery.timeout.ms", "60000", "linger.ms", "0", "retry.backoff.ms", "1", "max.in.flight.requests.per.connection", "5")) {
                for (int i = 1; i <= 3000; i++) p.publicar(i, "cliente-1");
                p.vaciar();
                assertEquals(3000, p.confirmados.get());
            }
            var todo = Kf.leerTodo(tema);
            duplicados[k] = todo.size() - todo.stream().map(ConsumerRecord::value).distinct().count();
            Ev.nota("11. idempotencia=" + idem[k] + ": enviados 3000, en el tema " + todo.size() + " (duplicados " + duplicados[k] + ")");
        }
        assertEquals(0, duplicados[1], "con idempotencia no hay duplicados");
        assertTrue(duplicados[0] > 0, "sin idempotencia y con reintentos por tiempo agotado aparecen duplicados");
    }

    @Test
    @Order(7)
    void paso12_mensajeMasGrandeQueElLimite() throws Exception {
        Kf.tema("grandes", 1);
        String grande = "x".repeat(2 * 1024 * 1024);
        // (a) límite del cliente: max.request.size=1 MiB
        try (var p = new ProductorPedidos(Kf.bootstrap(), "grandes")) {
            var f = p.productor.send(new ProducerRecord<>("grandes", "k", grande), (m, e) -> { });
            String e = org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class, f::get).getCause().toString();
            Ev.nota("12a. el cliente rechaza antes de enviar: " + e);
            assertTrue(e.contains("RecordTooLargeException"));
        }
        // (b) el cliente lo deja pasar (max.request.size=4 MiB), el broker lo rechaza (message.max.bytes ~1 MiB)
        try (var p = new ProductorPedidos(Kf.bootstrap(), "grandes", "max.request.size", "4194304")) {
            var f = p.productor.send(new ProducerRecord<>("grandes", "k", grande), (m, e) -> { });
            String e = org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class, f::get).getCause().toString();
            Ev.nota("12b. el broker rechaza: " + e);
            assertTrue(e.contains("RecordTooLargeException") || e.contains("MESSAGE_TOO_LARGE") || e.contains("too large"));
        }
        // (c) el código del "antes": send() sin callback ni get(), acks=0: el error no se ve en ningún sitio
        try (var p = new ProductorPedidos(Kf.bootstrap(), "grandes", "max.request.size", "4194304", "acks", "0", "enable.idempotence", "false")) {
            p.productor.send(new ProducerRecord<>("grandes", "k", grande));
            p.vaciar();
        }
        Ev.nota("12c. 'antes' (acks=0, sin callback): sin excepción, mensajes en el tema: " + Kf.contar("grandes"));
        assertEquals(0, Kf.contar("grandes"));
        // (d) todo error queda registrado con el callback del paso 7
        try (var p = new ProductorPedidos(Kf.bootstrap(), "grandes", "max.request.size", "4194304")) {
            p.productor.send(new ProducerRecord<>("grandes", "k", grande), (m, e) -> p.errores.add("registrado: " + e));
            p.vaciar();
            Thread.sleep(500);
            assertEquals(1, p.errores.size());
            Ev.nota("12d. con callback: " + p.errores.get(0));
        }
    }
}
