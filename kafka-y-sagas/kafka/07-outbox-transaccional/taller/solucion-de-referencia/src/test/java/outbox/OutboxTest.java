package outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import comun.Ev;
import comun.Kf;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OutboxTest {

    static final String B = Kf.bootstrap();
    static final String CONTENEDOR = "pub-kafka-y-sagas-kafka-1";

    @BeforeEach
    void limpio() throws Exception {
        arrancarKafka();
        Bd.esquemaNuevo();
        Kf.tema(B, Pedidos.TEMA, 3, 1, Map.of());
    }

    @AfterEach
    void kafkaArriba() {
        arrancarKafka();
    }

    static void detenerKafka() {
        Kf.docker("stop", CONTENEDOR);
    }

    static void arrancarKafka() {
        Kf.docker("start", CONTENEDOR);
        Kf.esperar(() -> { try (var a = Kf.admin(B)) { return !a.listTopics().names().get(5, TimeUnit.SECONDS).isEmpty() || true; } }, 90_000, "Kafka arriba");
    }

    static KafkaProducer<String, String> productorRapido() {
        return new KafkaProducer<>(Map.of("bootstrap.servers", B, "key.serializer", StringSerializer.class.getName(), "value.serializer", StringSerializer.class.getName(),
                "max.block.ms", "3000", "delivery.timeout.ms", "5000", "request.timeout.ms", "3000"));
    }

    static long contar(String sql) throws Exception {
        try (Connection c = Bd.conectar(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    static List<ConsumerRecord<String, String>> enElTema() {
        return Kf.leerTodo(B, Pedidos.TEMA, "read_uncommitted");
    }

    static String tipo(ConsumerRecord<String, String> r) {
        return r.value().replaceAll(".*\"tipo\":\"(\\w+)\".*", "$1");
    }

    // ------------------------------------------------------------------ pasos 1, 2 y 3
    @Test
    @Order(1)
    void pasos1y2_dobleEscrituraPierdeEventos() throws Exception {
        // (a) Kafka detenido
        detenerKafka();
        try (var p = productorRapido()) {
            assertThrows(Exception.class, () -> Pedidos.crearConDobleEscritura("p-a", p, false));
        }
        arrancarKafka();
        Ev.nota("2a. Kafka detenido al crear p-a: pedidos en la base " + contar("SELECT count(*) FROM pedidos") + ", eventos en el tema " + enElTema().size());
        assertEquals(1, contar("SELECT count(*) FROM pedidos"));
        assertEquals(0, enElTema().size());
        // (b) el proceso muere entre el guardado y la publicación
        Process hijo = Kf.hijo(Escenarios.class, "doble-escritura-muere", "p-b");
        Kf.recoger(hijo);
        hijo.waitFor(60, TimeUnit.SECONDS);
        assertEquals(137, hijo.exitValue());
        Ev.nota("2b. proceso muerto entre guardar y publicar p-b: pedidos " + contar("SELECT count(*) FROM pedidos") + ", eventos en el tema " + enElTema().size() + " (dos pedidos sin evento y nadie lo sabe)");
        assertEquals(2, contar("SELECT count(*) FROM pedidos"));
        assertEquals(0, enElTema().size());
    }

    @Test
    @Order(2)
    void paso3_publicarPrimeroDejaUnEventoFantasma() throws Exception {
        try (var p = productorRapido()) {
            assertThrows(Exception.class, () -> Pedidos.crearPublicandoPrimero("p-fantasma", p, true));
        }
        Ev.nota("3. publicar primero y fallar la base: pedidos " + contar("SELECT count(*) FROM pedidos") + ", eventos en el tema " + enElTema().size() + " (PedidoCreado de un pedido que no existe)");
        assertEquals(0, contar("SELECT count(*) FROM pedidos"));
        assertEquals(1, enElTema().size());
    }

    // ------------------------------------------------------------------ pasos 4 y 5
    @Test
    @Order(3)
    void paso4_outboxEsAtomico() throws Exception {
        Pedidos.crearConOutbox("p-1", "cliente-1", false);
        assertEquals(1, contar("SELECT count(*) FROM pedidos"));
        assertEquals(1, contar("SELECT count(*) FROM salida"));
        assertThrows(IllegalStateException.class, () -> Pedidos.crearConOutbox("p-2", "cliente-1", true));
        assertThrows(Exception.class, () -> Pedidos.crearConOutbox("p-3", null, false));     // la base rechaza el pedido DESPUÉS de... antes del evento: tampoco queda nada
        Ev.nota("4. tras un pedido bueno y dos transacciones fallidas: pedidos " + contar("SELECT count(*) FROM pedidos") + ", filas de salida " + contar("SELECT count(*) FROM salida") + " (ninguna de las dos cosas sin la otra)");
        assertEquals(1, contar("SELECT count(*) FROM pedidos"));
        assertEquals(1, contar("SELECT count(*) FROM salida"));
    }

    @Test
    @Order(4)
    void paso5_elEventoSaleCuandoKafkaVuelveOElProcesoSeReinicia() throws Exception {
        detenerKafka();
        Pedidos.crearConOutbox("p-1", "cliente-1", false);                       // el pedido se acepta con Kafka caído
        try (var pub = new Publicador(B, Publicador.Reclamo.CORRECTO, 100, false)) {
            assertEquals(0, pub.unaVuelta(), "no puede publicar");
            Ev.nota("5. Kafka caído: pedido aceptado, publicador sin éxito, filas pendientes " + Publicador.pendientes()[0]);
            assertEquals(1, Publicador.pendientes()[0]);
        }
        arrancarKafka();
        try (var pubNuevo = new Publicador(B, Publicador.Reclamo.CORRECTO, 100, false)) {     // "se reinicia el proceso": otro publicador nuevo
            assertEquals(1, pubNuevo.unaVuelta());
        }
        assertEquals(1, enElTema().size());
        // varias caídas seguidas: ningún pedido guardado se queda sin evento
        int creados = 1;
        for (int ronda = 0; ronda < 3; ronda++) {
            detenerKafka();
            for (int i = 0; i < 10; i++) Pedidos.crearConOutbox("r" + ronda + "-" + i, "c", false);
            creados += 10;
            arrancarKafka();
            try (var pub = new Publicador(B, Publicador.Reclamo.CORRECTO, 100, false)) {
                while (pub.unaVuelta() > 0) { /* vaciar */ }
            }
        }
        Ev.nota("5. tras tres caídas de Kafka con 10 pedidos cada una: pedidos " + contar("SELECT count(*) FROM pedidos") + ", eventos en el tema " + enElTema().size() + ", filas pendientes " + Publicador.pendientes()[0]);
        assertEquals(creados, contar("SELECT count(*) FROM pedidos"));
        assertEquals(creados, enElTema().size());
        assertEquals(0, Publicador.pendientes()[0]);
    }

    // ------------------------------------------------------------------ paso 6
    @Test
    @Order(5)
    void paso6_duplicadoEnElTemaYConsumidorIdempotente() throws Exception {
        Pedidos.crearConOutbox("p-1", "cliente-1", false);
        Process hijo = Kf.hijo(Escenarios.class, "publicador-muere");
        Kf.recoger(hijo);
        hijo.waitFor(60, TimeUnit.SECONDS);
        assertEquals(137, hijo.exitValue());
        assertEquals(1, enElTema().size(), "se publicó");
        assertEquals(1, Publicador.pendientes()[0], "y no se marcó");
        try (var pub = new Publicador(B, Publicador.Reclamo.CORRECTO, 100, false)) {
            assertEquals(1, pub.unaVuelta());                                  // el publicador vuelve y lo envía otra vez
        }
        var mensajes = enElTema();
        long efectos = 0;
        for (var r : mensajes) {
            String id = r.value().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
            if (ConsumidorIdempotente.aplicar(id, r.key(), tipo(r))) efectos++;
        }
        Ev.nota("6. publicador muerto tras publicar y antes de marcar: el tema tiene " + mensajes.size() + " mensajes con el mismo id; el consumidor idempotente aplicó " + efectos + " efecto(s)");
        assertEquals(2, mensajes.size());
        assertEquals(1, efectos);
        assertEquals(1, contar("SELECT count(*) FROM efectos"));
    }

    // ------------------------------------------------------------------ paso 7
    private void insertarPendientes(int n) throws Exception {
        try (Connection c = Bd.conectar(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO salida(agregado, tipo, payload) SELECT 'p-' || g, 'PedidoCreado', '{\"id\":\"' || gen_random_uuid() || '\",\"tipo\":\"PedidoCreado\"}' FROM generate_series(1, " + n + ") g");
            c.commit();
        }
    }

    private static void correrDosPublicadores(Publicador.Reclamo reclamo) throws Exception {
        try (var a = new Publicador(B, reclamo, 100, false); var b = new Publicador(B, reclamo, 100, false)) {
            CyclicBarrier salida = new CyclicBarrier(2);
            Runnable vaciar = null;
            List<Thread> hilos = new ArrayList<>();
            for (Publicador pub : List.of(a, b)) {
                Thread t = new Thread(() -> {
                    try {
                        salida.await();
                        while (Publicador.pendientes()[0] > 0) pub.unaVuelta();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                });
                t.start();
                hilos.add(t);
            }
            for (Thread t : hilos) t.join(120_000);
        }
    }

    @Test
    @Order(6)
    void paso7_dosPublicadoresSePisanSinBloqueoYNoConSkipLocked() throws Exception {
        insertarPendientes(1000);
        correrDosPublicadores(Publicador.Reclamo.INGENUO);
        long ingenuos = enElTema().size();
        Ev.nota("7. dos publicadores con el reclamo ingenuo (SELECT sin bloqueo), 1000 filas: " + ingenuos + " mensajes en el tema (" + (ingenuos - 1000) + " duplicados por competencia)");
        assertTrue(ingenuos > 1000);

        Kf.tema(B, Pedidos.TEMA, 3, 1, Map.of());
        try (Connection c = Bd.conectar(); Statement st = c.createStatement()) { st.execute("DELETE FROM salida"); c.commit(); }
        insertarPendientes(1000);
        correrDosPublicadores(Publicador.Reclamo.CORRECTO);
        var mensajes = enElTema();
        long distintos = mensajes.stream().map(r -> r.value()).distinct().count();
        Ev.nota("7. con FOR UPDATE SKIP LOCKED: " + mensajes.size() + " mensajes, " + distintos + " distintos");
        assertEquals(1000, mensajes.size());
        assertEquals(1000, distintos);
    }

    // ------------------------------------------------------------------ paso 8
    private List<String> ordenDeUnPedido(String id) {
        return enElTema().stream().filter(r -> r.key().equals(id)).map(OutboxTest::tipo).collect(Collectors.toList());
    }

    private void tresEventos(String id) throws Exception {
        Pedidos.crearConOutbox(id, "c", false);
        Pedidos.cambiarEstado(id, "CONFIRMADO", "PedidoConfirmado");
        Pedidos.cambiarEstado(id, "CANCELADO", "PedidoCancelado");
    }

    @Test
    @Order(7)
    void paso8_ordenPorPedido() throws Exception {
        List<String> esperado = List.of("PedidoCreado", "PedidoConfirmado", "PedidoCancelado");
        // a) un publicador
        tresEventos("p-1");
        try (var pub = new Publicador(B, Publicador.Reclamo.CORRECTO, 100, false)) {
            while (Publicador.pendientes()[0] > 0) pub.unaVuelta();
        }
        Ev.nota("8a. un publicador: " + ordenDeUnPedido("p-1"));
        assertEquals(esperado, ordenDeUnPedido("p-1"));
        // b) dos publicadores a la vez, con muchos pedidos de tres eventos
        for (int i = 0; i < 60; i++) tresEventos("q-" + i);
        try (var a = new Publicador(B, Publicador.Reclamo.CORRECTO, 20, false); var b = new Publicador(B, Publicador.Reclamo.CORRECTO, 20, false)) {
            a.arrancar(10); b.arrancar(10);
            Kf.esperar(() -> Publicador.pendientes()[0] == 0, 120_000, "vaciar");
        }
        int desordenados = 0;
        for (int i = 0; i < 60; i++) if (!ordenDeUnPedido("q-" + i).equals(esperado)) desordenados++;
        Ev.nota("8b. dos publicadores a la vez, 60 pedidos x 3 eventos: pedidos con el orden alterado en el tema = " + desordenados);
        assertEquals(0, desordenados);
        // c) el segundo falla y el tercero no
        tresEventos("r-1");
        try (var pub = new Publicador(B, Publicador.Reclamo.CORRECTO, 100, false)) {
            pub.falla = t -> t.equals("PedidoConfirmado");
            for (int i = 0; i < 5; i++) pub.unaVuelta();
            Ev.nota("8c. publicador correcto con el segundo fallando: en el tema " + ordenDeUnPedido("r-1") + " (el tercero espera)");
            assertEquals(List.of("PedidoCreado"), ordenDeUnPedido("r-1"));
            pub.falla = t -> false;
            while (Publicador.pendientes()[0] > 0) pub.unaVuelta();
            assertEquals(esperado, ordenDeUnPedido("r-1"));
        }
        tresEventos("s-1");
        try (var ingenuo = new Publicador(B, Publicador.Reclamo.INGENUO, 100, true)) {
            ingenuo.falla = new java.util.function.Predicate<>() {
                boolean yaFallo;
                @Override public boolean test(String t) {
                    if (t.equals("PedidoConfirmado") && !yaFallo) { yaFallo = true; return true; }
                    return false;
                }
            };
            for (int i = 0; i < 5; i++) ingenuo.unaVuelta();
        }
        Ev.nota("8c. publicador que se salta la fila fallida y sigue: " + ordenDeUnPedido("s-1") + " (el cancelado sale ANTES que el confirmado)");
        assertEquals(List.of("PedidoCreado", "PedidoCancelado", "PedidoConfirmado"), ordenDeUnPedido("s-1"));
    }

    // ------------------------------------------------------------------ paso 9
    @Test
    @Order(8)
    void paso9_retrasoDeConfirmacionHastaLlegada() throws Exception {
        for (long intervalo : new long[] {1000, 50}) {
            Kf.tema(B, Pedidos.TEMA, 3, 1, Map.of());
            Bd.esquemaNuevo();
            var props = Kf.consumo(B, null, "read_uncommitted");
            props.put("group.id", Kf.unico("lat"));
            props.put("auto.offset.reset", "latest");
            var llegada = new java.util.concurrent.atomic.AtomicBoolean(true);
            // el retraso: hora de la confirmación de la transacción contra hora de llegada al consumidor, en el mismo equipo
            List<Long> llegadas = new CopyOnWriteArrayList<>();
            Thread lector2 = new Thread(() -> {
                try (var c = new KafkaConsumer<String, String>(props)) {
                    c.subscribe(List.of(Pedidos.TEMA));
                    while (llegada.get()) for (var r : c.poll(Duration.ofMillis(20))) llegadas.add(System.currentTimeMillis());
                }
            });
            lector2.start();
            Thread.sleep(3000);                                                    // que el consumidor esté suscrito
            List<Long> creadas = new ArrayList<>();
            try (var pub = new Publicador(B, Publicador.Reclamo.CORRECTO, 100, false)) {
                pub.arrancar(intervalo);
                for (int i = 0; i < 25; i++) {
                    Pedidos.crearConOutbox("l-" + i, "c", false);
                    creadas.add(System.currentTimeMillis());                       // justo tras confirmar la transacción
                    Thread.sleep(137);
                }
                Kf.esperar(() -> llegadas.size() == 25, 60_000, "llegadas");
                long t0 = System.currentTimeMillis();
                Thread.sleep(5000);
                long consultasEnReposo = pub.consultas.get();
                Thread.sleep(5000);
                consultasEnReposo = pub.consultas.get() - consultasEnReposo;
                llegada.set(false);
                lector2.join(10_000);
                List<Long> ret = new ArrayList<>();
                List<Long> lleg = new ArrayList<>(llegadas);
                Collections.sort(lleg);
                for (int i = 0; i < 25; i++) ret.add(lleg.get(i) - creadas.get(i));
                Collections.sort(ret);
                Ev.nota("9. intervalo de sondeo " + intervalo + " ms: retraso confirmación -> llegada: mediana " + ret.get(12) + " ms, máximo " + ret.get(24) + " ms; consultas a la base en reposo: " + consultasEnReposo / 5.0 + " por segundo");
                if (intervalo == 1000) assertTrue(ret.get(12) > 100, "con sondeo lento el retraso se nota"); else assertTrue(ret.get(12) < 400);
            } finally {
                llegada.set(false);
            }
        }
    }

    // ------------------------------------------------------------------ paso 10
    @Test
    @Order(9)
    void paso10_limpiezaYMetricaDePendientes() throws Exception {
        for (int i = 0; i < 30; i++) Pedidos.crearConOutbox("m-" + i, "c", false);
        Thread.sleep(1500);
        double[] m = Publicador.pendientes();
        Ev.nota("10. sin publicador: filas pendientes " + (long) m[0] + ", la más vieja tiene " + String.format("%.1f", m[1]) + " s -> alerta (regla: pendientes > 100 durante 2 min, o la más vieja > 60 s)");
        assertEquals(30, m[0]);
        assertTrue(m[1] >= 1.5);
        try (var pub = new Publicador(B, Publicador.Reclamo.CORRECTO, 100, false)) {
            while (Publicador.pendientes()[0] > 0) pub.unaVuelta();
        }
        assertEquals(0, Publicador.pendientes()[0]);
        assertEquals(0, Publicador.limpiar(3600), "nada de más de una hora todavía");
        Thread.sleep(1200);
        int borradas = Publicador.limpiar(1);
        Ev.nota("10. limpieza de publicadas con retención de 1 s: " + borradas + " filas borradas; quedan " + contar("SELECT count(*) FROM salida"));
        assertEquals(30, borradas);
        assertEquals(0, contar("SELECT count(*) FROM salida"));
    }
}
