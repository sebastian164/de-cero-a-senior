package sagas;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sagas.Servicios.Simulado;
import soporte.Entorno;

/** Taller n9-04: Outbox y sagas contra Kafka 4.3.1 y PostgreSQL 18.7 reales. */
class SagasOutboxTest {

    static String url;

    @BeforeAll
    static void base() throws Exception {
        url = Entorno.baseNueva("n9_04");
        try (Connection c = Entorno.conectar(url); var s = c.createStatement()) {
            s.execute(Pedidos.ESQUEMA);
            s.execute(Saga.ESQUEMA);
            s.execute(Servicios.EnBase.ESQUEMA);
        }
    }

    @Test
    void el_ddl_de_outbox_es_exactamente_el_del_readme_y_se_puede_ejecutar_tal_cual() throws Exception {
        String ddl = soporte.Readme.bloques("java/nivel-09-sistemas-distribuidos/04-consistencia-sagas-outbox", "sql").get(0);
        String normal = ddl.replaceAll("\\s+", " ").trim();
        assertThat(Pedidos.ESQUEMA.replaceAll("\\s+", " ").replace("CREATE TABLE IF NOT EXISTS outbox", "CREATE TABLE outbox"))
                .as("el esquema usado por la solución contiene el DDL del README sin cambios").contains(normal);
        String otra = Entorno.baseNueva("n9_04_ddl");
        try (Connection c = Entorno.conectar(otra); var st = c.createStatement()) {
            st.execute(ddl);                                                      // el bloque del README, tal cual
            st.execute("INSERT INTO outbox (id, tipo, agregado_id, contenido) VALUES (gen_random_uuid(), 'PedidoCreado', '1', '{}')");
        }
    }

    static String tema(String prefijo) throws Exception {
        String nombre = prefijo + "-" + System.nanoTime();
        try (Admin a = Admin.create(Map.<String, Object>of("bootstrap.servers", Entorno.KAFKA))) {
            a.createTopics(List.of(new NewTopic(nombre, 1, (short) 1))).all().get();
        }
        return nombre;
    }

    static List<ConsumerRecord<String, String>> leerTodo(String tema, Duration plazo) {
        var p = new java.util.Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, Entorno.KAFKA);
        p.put(ConsumerConfig.GROUP_ID_CONFIG, "lector-" + System.nanoTime());
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        List<ConsumerRecord<String, String>> r = new ArrayList<>();
        try (var c = new KafkaConsumer<String, String>(p)) {
            c.subscribe(List.of(tema));
            long fin = System.nanoTime() + plazo.toNanos();
            while (System.nanoTime() < fin) {
                c.poll(Duration.ofMillis(300)).forEach(r::add);
            }
        }
        return r;
    }

    static int contar(String sql) throws Exception {
        try (Connection c = Entorno.conectar(url); var s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Lanza el proceso hijo, espera a que diga GUARDADO y lo mata con SIGKILL. */
    static void matarTrasGuardar(String modo, String tema, long id) throws Exception {
        String java = System.getProperty("java.home") + "/bin/java";
        Process p = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), "sagas.Pedidos",
                modo, url, Entorno.KAFKA, tema, String.valueOf(id)).redirectErrorStream(true).start();
        try (var r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String linea;
            while ((linea = r.readLine()) != null && !linea.equals("GUARDADO")) {
                System.out.println("hijo: " + linea);
            }
            assertThat(linea).as("el hijo debe llegar a GUARDADO").isEqualTo("GUARDADO");
        }
        p.destroyForcibly();                                           // kill -9
        assertThat(p.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(p.exitValue()).isNotZero();
    }

    // ---------- 1. La doble escritura ----------
    @Test
    void paso1_si_el_proceso_muere_entre_guardar_y_publicar_el_pedido_existe_y_el_evento_nunca_salio() throws Exception {
        String tema = tema("doble");
        matarTrasGuardar("doble", tema, 1001);
        assertThat(contar("SELECT count(*) FROM pedidos WHERE id = 1001")).as("el pedido quedó guardado").isEqualTo(1);
        assertThat(leerTodo(tema, Duration.ofSeconds(4))).as("el evento nunca salió").isEmpty();
    }

    @Test
    void paso1b_invertir_el_orden_produce_el_error_opuesto_el_evento_sale_y_el_pedido_no_existe() throws Exception {
        String tema = tema("doble-inv");
        var pedidos = new Pedidos(url, Entorno.KAFKA, tema);
        try (var p = pedidos.productor()) {
            p.send(new org.apache.kafka.clients.producer.ProducerRecord<>(tema, "1002", Pedidos.json(1002, 100))).get();
        }
        // el proceso cae aquí, antes de guardar
        assertThat(leerTodo(tema, Duration.ofSeconds(4))).hasSize(1);
        assertThat(contar("SELECT count(*) FROM pedidos WHERE id = 1002")).isZero();
    }

    // ---------- 2. Outbox ----------
    @Test
    void paso2_con_outbox_el_evento_sale_aunque_el_proceso_haya_muerto_tras_guardar() throws Exception {
        String tema = tema("outbox");
        matarTrasGuardar("outbox", tema, 2001);
        assertThat(contar("SELECT count(*) FROM pedidos WHERE id = 2001")).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM outbox WHERE agregado_id = '2001' AND publicado_en IS NULL")).isEqualTo(1);

        // Otro proceso (el publicador) lo recoge.
        var pedidos = new Pedidos(url, Entorno.KAFKA, tema);
        assertThat(pedidos.publicarPendientes(id -> { })).isEqualTo(1);
        assertThat(leerTodo(tema, Duration.ofSeconds(4))).hasSize(1);
        assertThat(contar("SELECT count(*) FROM outbox WHERE agregado_id = '2001' AND publicado_en IS NOT NULL")).isEqualTo(1);
        assertThat(pedidos.publicarPendientes(id -> { })).as("ya marcado: no se reenvía").isZero();
    }

    @Test
    void paso2b_si_el_publicador_cae_entre_enviar_y_marcar_el_evento_sale_dos_veces_y_el_inbox_lo_absorbe() throws Exception {
        String tema = tema("outbox-dup");
        var pedidos = new Pedidos(url, Entorno.KAFKA, tema);
        pedidos.crearConOutbox(2002, 50);
        try {
            pedidos.publicarPendientes(id -> { throw new IllegalStateException("el publicador cae tras enviar"); });
        } catch (IllegalStateException esperado) {
            // el evento ya está en Kafka, pero la fila sigue sin marcar
        }
        assertThat(contar("SELECT count(*) FROM outbox WHERE agregado_id = '2002' AND publicado_en IS NULL")).isEqualTo(1);
        assertThat(pedidos.publicarPendientes(id -> { })).isEqualTo(1);        // el publicador reinicia y reenvía

        var llegados = leerTodo(tema, Duration.ofSeconds(4));
        assertThat(llegados).as("al menos una vez: el mismo evento dos veces").hasSize(2);
        Set<String> ids = new HashSet<>();
        llegados.forEach(r -> ids.add(new String(r.headers().lastHeader("idEvento").value())));
        assertThat(ids).as("pero con el mismo idEvento, que es lo que permite deduplicar").hasSize(1);

        // Inbox: el consumidor registra los ids ya vistos y solo aplica el primero.
        Set<String> inbox = new HashSet<>();
        AtomicInteger efectos = new AtomicInteger();
        llegados.forEach(r -> { if (inbox.add(new String(r.headers().lastHeader("idEvento").value()))) efectos.incrementAndGet(); });
        assertThat(efectos).hasValue(1);
    }

    // ---------- 3 a 5. Saga orquestada ----------
    record Mundo(Simulado inventario, Simulado pagos, Simulado envios, Saga saga) { }

    static Mundo mundo(int intentosCompensacion) {
        var inv = new Simulado("inventario");
        var pag = new Simulado("pagos");
        var env = new Simulado("envios");
        return new Mundo(inv, pag, env, new Saga(url, List.of(inv, pag, env), intentosCompensacion));
    }

    @Test
    void paso3_la_saga_de_tres_pasos_completa_y_deja_el_estado_persistido() throws Exception {
        Mundo m = mundo(3);
        assertThat(m.saga().ejecutar(3001)).isEqualTo("COMPLETADA");
        assertThat(m.inventario().aplicados).contains(3001L);
        assertThat(m.pagos().aplicados).contains(3001L);
        assertThat(m.envios().aplicados).contains(3001L);
        assertThat(Saga.dondeEsta(url, 3001)).startsWith("COMPLETADA");
    }

    @Test
    void paso4_si_falla_el_tercer_paso_se_compensan_los_dos_primeros_en_orden_inverso_y_queda_consistente() throws Exception {
        Mundo m = mundo(3);
        m.envios().fallaAlEjecutar = true;
        assertThat(m.saga().ejecutar(4001)).isEqualTo("CANCELADA");
        assertThat(m.inventario().aplicados).doesNotContain(4001L);          // inventario liberado
        assertThat(m.pagos().aplicados).doesNotContain(4001L);               // pago reembolsado
        assertThat(m.envios().aplicados).doesNotContain(4001L);
        assertThat(m.envios().llamadas).as("el paso que falló no se compensa: nunca se aplicó").doesNotContain("compensar:4001");
        assertThat(Saga.dondeEsta(url, 4001)).startsWith("CANCELADA");
    }

    @Test
    void paso5_una_compensacion_que_falla_la_primera_vez_se_reintenta_y_se_recupera_sin_duplicar_efectos() throws Exception {
        Mundo m = mundo(3);
        m.envios().fallaAlEjecutar = true;
        m.pagos().fallosDeCompensacion = 1;                                   // el reembolso falla una vez
        assertThat(m.saga().ejecutar(5001)).isEqualTo("CANCELADA");
        assertThat(m.pagos().llamadas).containsExactly("ejecutar:5001", "compensar:5001", "compensar:5001");
        assertThat(m.pagos().aplicados).doesNotContain(5001L);
        assertThat(m.inventario().aplicados).doesNotContain(5001L);

        // Si la compensación no se recupera, la saga no miente: queda COMPENSACION_PENDIENTE y visible.
        Mundo roto = mundo(2);
        roto.envios().fallaAlEjecutar = true;
        roto.pagos().fallosDeCompensacion = 99;
        assertThat(roto.saga().ejecutar(5002)).isEqualTo("COMPENSACION_PENDIENTE");
        assertThat(Saga.dondeEsta(url, 5002)).contains("COMPENSACION_PENDIENTE").contains("pagos");
        assertThat(roto.inventario().aplicados).as("el inventario NO se liberó porque el reembolso quedó pendiente").contains(5002L);

        // Una compensación repetida es inofensiva (idempotente).
        m.pagos().compensar(5001);
        m.pagos().compensar(5001);
        assertThat(m.pagos().aplicados).doesNotContain(5001L);
    }

    @Test
    void paso5b_una_saga_con_compensacion_pendiente_se_reanuda_desde_la_tabla_cuando_la_dependencia_se_recupera() throws Exception {
        Mundo roto = mundo(2);
        roto.envios().fallaAlEjecutar = true;
        roto.pagos().fallosDeCompensacion = 99;
        assertThat(roto.saga().ejecutar(6001)).isEqualTo("COMPENSACION_PENDIENTE");
        assertThat(roto.inventario().aplicados).contains(6001L);

        roto.pagos().fallosDeCompensacion = 0;                                // la dependencia se recupera
        // Se reanuda con un orquestador NUEVO que no recuerda nada: solo lo que dice la tabla sagas.
        Saga nuevo = new Saga(url, List.of(roto.inventario(), roto.pagos(), roto.envios()), 2);
        assertThat(nuevo.reanudar(6001)).isEqualTo("CANCELADA");
        assertThat(Saga.dondeEsta(url, 6001)).startsWith("CANCELADA");
        assertThat(roto.pagos().aplicados).doesNotContain(6001L);
        assertThat(roto.inventario().aplicados).as("el inventario se libera tras el reembolso").doesNotContain(6001L);
        assertThat(nuevo.reanudar(6001)).as("reanudar una saga terminada no hace nada").isEqualTo("CANCELADA");
    }

    /** Lanza el orquestador hijo, espera a que esté ejecutando el tercer paso y lo mata con SIGKILL. */
    static void matarOrquestadorTrasElPaso2(long id) throws Exception {
        String java = System.getProperty("java.home") + "/bin/java";
        Process p = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), "sagas.SagaHija", url, String.valueOf(id))
                .redirectErrorStream(true).start();
        try (var r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String linea;
            while ((linea = r.readLine()) != null && !linea.equals("PASO2-LISTO")) System.out.println("hijo: " + linea);
            assertThat(linea).as("el orquestador hijo debe llegar al tercer paso").isEqualTo("PASO2-LISTO");
        }
        p.destroyForcibly();                                                  // kill -9
        assertThat(p.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(p.exitValue()).isNotZero();
    }

    @Test
    void paso5c_si_el_orquestador_muere_con_sigkill_tras_el_paso_2_un_proceso_nuevo_reanuda_y_completa() throws Exception {
        matarOrquestadorTrasElPaso2(6002);
        assertThat(Saga.dondeEsta(url, 6002)).startsWith("EN_CURSO / envios / ejecutando");
        assertThat(Servicios.EnBase.efectos(url, 6002)).containsExactly("inventario", "pagos");

        var inv = new Servicios.EnBase(url, "inventario");
        var pag = new Servicios.EnBase(url, "pagos");
        var env = new Servicios.EnBase(url, "envios");
        assertThat(new Saga(url, List.of(inv, pag, env), 3).reanudar(6002)).isEqualTo("COMPLETADA");
        assertThat(Servicios.EnBase.efectos(url, 6002)).containsExactly("envios", "inventario", "pagos");
        assertThat(Saga.dondeEsta(url, 6002)).startsWith("COMPLETADA");
    }

    @Test
    void paso5d_si_el_orquestador_muere_tras_el_paso_2_y_al_reanudar_el_tercer_paso_falla_se_compensa_todo() throws Exception {
        matarOrquestadorTrasElPaso2(6003);
        var inv = new Servicios.EnBase(url, "inventario");
        var pag = new Servicios.EnBase(url, "pagos");
        var env = new Servicios.EnBase(url, "envios");
        env.falla = true;
        assertThat(new Saga(url, List.of(inv, pag, env), 3).reanudar(6003)).isEqualTo("CANCELADA");
        assertThat(Servicios.EnBase.efectos(url, 6003)).as("inventario liberado y pago reembolsado").isEmpty();
    }

    // ---------- 6. Coreografía ----------
    @Test
    void paso6_la_coreografia_llega_al_mismo_resultado_pero_saber_donde_esta_el_pedido_exige_recorrer_los_eventos() throws Exception {
        var coreo = new Coreografia(true);
        coreo.crearPedido(42);
        assertThat(coreo.inventario.get(42L)).isEqualTo("LIBERADO");
        assertThat(coreo.pagos.get(42L)).isEqualTo("REEMBOLSADO");
        assertThat(coreo.bus.publicados).extracting(Coreografia.Evento::tipo)
                .containsExactly("PedidoCreado", "InventarioReservado", "PagoAprobado", "EnvioFallido", "PagoReembolsado");
        // Ningún servicio sabe el estado global: cada mapa describe solo su parte.
        assertThat(coreo.envios.get(42L)).isEqualTo("FALLIDO");
        assertThat(coreo.dondeEsta(42)).isEqualTo("PagoReembolsado");        // se deduce del último evento (¿y si hay eventos concurrentes?)

        Mundo m = mundo(3);
        m.envios().fallaAlEjecutar = true;
        m.saga().ejecutar(42);
        assertThat(Saga.dondeEsta(url, 42)).startsWith("CANCELADA");         // una consulta, una tabla
    }
}
