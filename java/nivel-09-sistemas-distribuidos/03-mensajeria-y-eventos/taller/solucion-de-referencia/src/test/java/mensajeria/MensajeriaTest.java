package mensajeria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import soporte.Entorno;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Taller n9-03: los seis pasos, contra Kafka 4.3.1 y PostgreSQL 18.7 reales. */
class MensajeriaTest {

    static String url;

    @BeforeAll
    static void base() throws SQLException {
        url = Entorno.baseNueva("n9_03");
        try (Connection c = Entorno.conectar(url); var s = c.createStatement()) {
            s.execute("""
                CREATE TABLE facturas (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, pedido_id TEXT, total BIGINT);
                CREATE TABLE procesados (id_evento TEXT PRIMARY KEY);
                """);
        }
    }

    static String evento(String pedido, int secuencia) {
        return "{\"idEvento\":\"" + pedido + "-" + secuencia + "\",\"pedidoId\":\"" + pedido + "\",\"secuencia\":" + secuencia + ",\"total\":100}";
    }

    static void enviar(String tema, String clave, String valor) throws Exception {
        try (var p = Kafka.productor(Map.of())) {
            p.send(new ProducerRecord<>(tema, clave, valor)).get();
        }
    }

    // ---------- 1. Dos grupos de consumidores leen el mismo registro ----------
    @Test
    void paso1_dos_grupos_distintos_leen_cada_uno_todos_los_eventos() throws Exception {
        String tema = Kafka.tema("pedidos", 3);
        try (var p = Kafka.productor(Map.of())) {
            for (int i = 0; i < 12; i++) {
                p.send(new ProducerRecord<>(tema, "p" + i, evento("p" + i, 0)));
            }
            p.flush();
        }
        for (String grupo : List.of("facturacion", "notificaciones")) {
            try (KafkaConsumer<String, String> c = Kafka.consumidor(grupo + "-" + tema)) {
                c.subscribe(List.of(tema));
                assertThat(Kafka.leer(c, 12, Duration.ofSeconds(20))).as(grupo).hasSize(12);
            }
        }
        // Dentro de un mismo grupo, dos miembros se reparten las particiones: ninguno ve todo.
        String grupo = "reparto-" + tema;
        try (var a = Kafka.consumidor(grupo); var b = Kafka.consumidor(grupo)) {
            a.subscribe(List.of(tema));
            b.subscribe(List.of(tema));
            long fin = System.nanoTime() + Duration.ofSeconds(40).toNanos();
            while ((a.assignment().isEmpty() || b.assignment().isEmpty() || a.assignment().size() + b.assignment().size() < 3)
                    && System.nanoTime() < fin) {
                a.poll(Duration.ofMillis(200));
                b.poll(Duration.ofMillis(200));
            }
            assertThat(a.assignment()).isNotEmpty();
            assertThat(b.assignment()).isNotEmpty();
            assertThat(a.assignment().size() + b.assignment().size()).isEqualTo(3);
            assertThat(a.assignment()).doesNotContainAnyElementsOf(b.assignment());
        }
    }

    // ---------- 2. Caída a mitad de proceso: entrega duplicada; consumidor idempotente ----------
    interface Manejador { void procesar(Connection c, ConsumerRecord<String, String> r) throws SQLException; }

    static final JsonMapper JSON = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    static void aplicar(Connection c, ConsumerRecord<String, String> r, boolean idempotente) throws SQLException {
        var nodo = JSON.readTree(r.value());
        String id = nodo.get("idEvento").asString();
        if (idempotente) {
            try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM procesados WHERE id_evento = ?")) {
                ps.setString(1, id);
                if (ps.executeQuery().next()) {
                    return;                                           // duplicado: ya se facturó
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO facturas (pedido_id, total) VALUES (?, ?)")) {
            ps.setString(1, nodo.get("pedidoId").asString());
            ps.setLong(2, nodo.get("total").asLong());
            ps.executeUpdate();
        }
        if (idempotente) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO procesados (id_evento) VALUES (?)")) {
                ps.setString(1, id);
                ps.executeUpdate();                                   // misma transacción que la factura
            }
        }
    }

    /** Un consumidor procesa 3 de 5 mensajes y "se cae" antes de confirmar el desplazamiento. Otro lo reemplaza. */
    int consumirConCaida(boolean idempotente, String pedido) throws Exception {
        String tema = Kafka.tema("confirmados", 1);
        try (var p = Kafka.productor(Map.of())) {
            for (int i = 0; i < 5; i++) {
                p.send(new ProducerRecord<>(tema, pedido, evento(pedido, i)));
            }
            p.flush();
        }
        String grupo = "facturacion-" + tema;
        try (Connection db = Entorno.conectar(url)) {
            db.setAutoCommit(false);
            // Primer consumidor: procesa 3, confirma la base, y muere antes de confirmar el offset.
            try (var c = Kafka.consumidor(grupo)) {
                c.subscribe(List.of(tema));
                var lote = Kafka.leer(c, 5, Duration.ofSeconds(20));
                for (int i = 0; i < 3; i++) {
                    aplicar(db, lote.get(i), idempotente);
                    db.commit();
                }
                // Sin commitSync: el proceso "cae" aquí.
            }
            // Segundo consumidor del mismo grupo: arranca desde el último offset confirmado (ninguno).
            try (var c = Kafka.consumidor(grupo)) {
                c.subscribe(List.of(tema));
                var lote = Kafka.leer(c, 5, Duration.ofSeconds(20));
                assertThat(lote).hasSize(5);                           // reentrega de los 3 ya procesados
                for (var r : lote) {
                    aplicar(db, r, idempotente);
                    db.commit();
                }
                c.commitSync();                                        // confirmar DESPUÉS de procesar
            }
            try (var ps = db.prepareStatement("SELECT count(*) FROM facturas WHERE pedido_id = ?")) {
                ps.setString(1, pedido);
                ResultSet rs = ps.executeQuery();
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    @Test
    void paso2_sin_idempotencia_la_caida_duplica_tres_facturas_y_con_idempotencia_quedan_cinco() throws Exception {
        assertThat(consumirConCaida(false, "ingenuo")).as("entrega al menos una vez, sin idempotencia").isEqualTo(8);
        assertThat(consumirConCaida(true, "idempotente")).as("con tabla de procesados en la misma transacción").isEqualTo(5);
    }

    // ---------- 3. Sin clave y con clave, tres particiones ----------
    @Test
    void paso3_con_clave_todos_los_eventos_del_pedido_van_a_una_particion_y_en_orden_sin_clave_se_dispersan() throws Exception {
        String conClave = Kafka.tema("orden-con-clave", 3);
        String sinClave = Kafka.tema("orden-sin-clave", 3);
        try (var p = Kafka.productor(Map.of())) {
            for (int i = 0; i < 9; i++) {
                p.send(new ProducerRecord<>(conClave, "pedido-42", evento("pedido-42", i)));
            }
            p.flush();
        }
        // Sin clave, el partidor por defecto agrupa los envíos pequeños en una partición pegajosa (sticky);
        // para ver el reparto de forma determinista se usa el partidor rotatorio.
        try (var p = Kafka.productor(Map.of("partitioner.class", "org.apache.kafka.clients.producer.RoundRobinPartitioner"))) {
            for (int i = 0; i < 9; i++) {
                p.send(new ProducerRecord<>(sinClave, null, evento("pedido-42", i)));
            }
            p.flush();
        }
        List<ConsumerRecord<String, String>> a, b;
        try (var c = Kafka.consumidor("g-" + conClave)) { c.subscribe(List.of(conClave)); a = Kafka.leer(c, 9, Duration.ofSeconds(20)); }
        try (var c = Kafka.consumidor("g-" + sinClave)) { c.subscribe(List.of(sinClave)); b = Kafka.leer(c, 9, Duration.ofSeconds(20)); }

        Set<Integer> particionesCon = new TreeSet<>(), particionesSin = new TreeSet<>();
        a.forEach(r -> particionesCon.add(r.partition()));
        b.forEach(r -> particionesSin.add(r.partition()));
        assertThat(particionesCon).as("con clave").hasSize(1);
        assertThat(particionesSin).as("sin clave (reparto rotatorio)").hasSize(3);

        List<Integer> secuenciasCon = a.stream().map(r -> JSON.readTree(r.value()).get("secuencia").asInt()).toList();
        assertThat(secuenciasCon).isEqualTo(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8));

        // Tres consumidores en paralelo (uno por partición) sobre el tema sin clave: el orden global se pierde.
        // Cada partición conserva su orden interno, pero entre particiones no hay orden: la 0 es la más lenta.
        for (int part : particionesSin) {
            List<Integer> deLaParticion = b.stream().filter(r -> r.partition() == part)
                    .map(r -> JSON.readTree(r.value()).get("secuencia").asInt()).toList();
            assertThat(deLaParticion).isSorted();
        }
        List<Integer> ordenDeProceso = b.stream()
                .sorted((x, y) -> x.partition() != y.partition() ? Integer.compare(y.partition(), x.partition()) : Long.compare(x.offset(), y.offset()))
                .map(r -> JSON.readTree(r.value()).get("secuencia").asInt()).toList();
        System.out.println("Orden de proceso sin clave (la partición más ocupada llega al final): " + ordenDeProceso);
        assertThat(ordenDeProceso).as("el pedido se procesa desordenado").isNotEqualTo(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8));
    }

    // ---------- 4. Mensaje venenoso: reintentos y cola de mensajes fallidos ----------
    record Resultado(List<String> procesados, List<String> fallidos, int intentosDelVenenoso) { }

    /** Bucle de consumo con N reintentos y DLQ. Con {@code intentosMax = Integer.MAX_VALUE} se simula "sin DLQ" acotado por tiempo. */
    Resultado consumirConVeneno(String tema, String dlq, int intentosMax, Duration limite) throws Exception {
        List<String> procesados = new ArrayList<>();
        int[] intentosVeneno = {0};
        long fin = System.nanoTime() + limite.toNanos();
        try (var c = Kafka.consumidor("g-" + tema + "-" + intentosMax); var p = Kafka.productor(Map.of())) {
            c.subscribe(List.of(tema));
            while (System.nanoTime() < fin && procesados.size() < 4) {
                for (var r : c.poll(Duration.ofMillis(300))) {
                    boolean hecho = false;
                    for (int intento = 1; intento <= intentosMax && System.nanoTime() < fin; intento++) {
                        try {
                            if (r.value().contains("VENENO")) {
                                intentosVeneno[0]++;
                                throw new IllegalStateException("no se puede deserializar");
                            }
                            procesados.add(r.key());
                            hecho = true;
                            break;
                        } catch (IllegalStateException e) {
                            Thread.sleep(100L * intento);               // retroceso lineal corto
                        }
                    }
                    if (!hecho) {
                        if (System.nanoTime() >= fin) {
                            return new Resultado(procesados, List.of(), intentosVeneno[0]);   // sigue atascado
                        }
                        var enviado = new ProducerRecord<>(dlq, r.key(), r.value());
                        enviado.headers().add("error", "no se puede deserializar".getBytes());
                        enviado.headers().add("origen", (r.topic() + "-" + r.partition() + "@" + r.offset()).getBytes());
                        p.send(enviado).get();
                    }
                    c.commitSync(Map.of(new org.apache.kafka.common.TopicPartition(r.topic(), r.partition()),
                            new org.apache.kafka.clients.consumer.OffsetAndMetadata(r.offset() + 1)));
                }
            }
        }
        List<String> fallidos = new ArrayList<>();
        try (var c = Kafka.consumidor("dlq-" + UUID.randomUUID())) {
            c.subscribe(List.of(dlq));
            Kafka.leer(c, 1, Duration.ofSeconds(5)).forEach(r -> fallidos.add(r.key()));
        }
        return new Resultado(procesados, fallidos, intentosVeneno[0]);
    }

    void publicarConVeneno(String tema) throws Exception {
        try (var p = Kafka.productor(Map.of())) {
            for (String k : List.of("a", "b", "VENENO", "d", "e")) {
                p.send(new ProducerRecord<>(tema, "k-" + k, k.equals("VENENO") ? "{{{VENENO" : evento("k-" + k, 0))).get();
            }
        }
    }

    @Test
    void paso4_sin_cola_de_fallidos_la_particion_se_detiene_y_con_reintentos_y_dlq_los_demas_siguen() throws Exception {
        String sin = Kafka.tema("veneno-sin", 1), con = Kafka.tema("veneno-con", 1), dlq = Kafka.tema("veneno-dlq", 1);
        publicarConVeneno(sin);
        publicarConVeneno(con);
        Resultado bloqueado = consumirConVeneno(sin, "no-aplica", Integer.MAX_VALUE, Duration.ofSeconds(6));
        assertThat(bloqueado.procesados()).as("sin DLQ solo avanzaron los previos al veneno").containsExactly("k-a", "k-b");
        assertThat(bloqueado.intentosDelVenenoso()).isGreaterThan(5);

        Resultado ok = consumirConVeneno(con, dlq, 3, Duration.ofSeconds(25));
        assertThat(ok.procesados()).containsExactly("k-a", "k-b", "k-d", "k-e");
        assertThat(ok.intentosDelVenenoso()).isEqualTo(3);
        assertThat(ok.fallidos()).containsExactly("k-VENENO");
    }

    // ---------- 5. Evolución del esquema ----------
    record PedidoV1(String idEvento, String pedidoId, long total) { }

    record PedidoV1Estricto(String idEvento, String pedidoId, @com.fasterxml.jackson.annotation.JsonProperty(required = true) Long total) { }

    @Test
    void paso5_agregar_un_campo_no_rompe_al_lector_tolerante_y_quitar_uno_si() {
        String v2 = "{\"idEvento\":\"e1\",\"pedidoId\":\"7\",\"total\":100,\"moneda\":\"COP\"}";     // agrega "moneda"
        String v3 = "{\"idEvento\":\"e2\",\"pedidoId\":\"8\",\"moneda\":\"COP\"}";                    // quita "total"

        // El consumidor antiguo con un lector estricto sí se rompe al agregar un campo: hay que ser tolerante al leer.
        JsonMapper estricto = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        assertThatThrownBy(() -> estricto.readValue(v2, PedidoV1.class)).hasMessageContaining("moneda");
        // Con lector tolerante (lo que hace Spring Boot por defecto), agregar un campo es compatible hacia atrás.
        assertThat(JSON.readValue(v2, PedidoV1.class)).isEqualTo(new PedidoV1("e1", "7", 100));

        // Quitar un campo: con Jackson 3 y su configuración por defecto el consumidor antiguo falla (null en un long)...
        assertThatThrownBy(() -> JSON.readValue(v3, PedidoV1.class)).hasMessageContaining("null");
        // ...pero con la tolerancia que muchos proyectos activan (sin FAIL_ON_NULL_FOR_PRIMITIVES) lee total = 0 en silencio: facturaría cero.
        JsonMapper permisivo = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();
        assertThat(permisivo.readValue(v3, PedidoV1.class).total()).isZero();
        // Declarar el campo como obligatorio con un tipo envoltorio falla de forma ruidosa sin depender de la configuración.
        assertThatThrownBy(() -> permisivo.readValue(v3, PedidoV1Estricto.class)).hasMessageContaining("total");
    }
}
