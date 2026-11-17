package contrato;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import comun.Ev;
import comun.Kf;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import contrato.Lectores.Pedido;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ContratoTest {

    static final String B = Kf.bootstrap();
    static final String REGISTRO = System.getProperty("registro.url", "http://localhost:47081");

    // ---------------------------------------------------------------- utilidades
    static JsonSchema esquema(String nombre) throws Exception {
        String json = Files.readString(Path.of("esquemas", nombre + ".schema.json"));
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(json);
    }

    static boolean valida(String esquema, String evento) throws Exception {
        return esquema(esquema).validate(evento, InputFormat.JSON).isEmpty();
    }

    static KafkaProducer<String, String> productor() {
        return new KafkaProducer<>(Map.of("bootstrap.servers", B, "key.serializer", StringSerializer.class.getName(),
                "value.serializer", StringSerializer.class.getName(), "acks", "all", "enable.idempotence", "true"));
    }

    static void publicar(String tema, String clave, String... eventos) {
        try (var p = productor()) {
            for (String e : eventos) p.send(new ProducerRecord<>(tema, clave, e));
            p.flush();
        }
    }

    /** Un consumidor vivo, de una versión concreta: cuenta lo que lee bien y guarda cada fallo (nunca se detiene por uno). */
    static final class Vivo implements AutoCloseable {
        final AtomicInteger bien = new AtomicInteger();
        final List<String> fallos = new CopyOnWriteArrayList<>();
        final List<Pedido> leidos = new CopyOnWriteArrayList<>();
        final AtomicBoolean seguir = new AtomicBoolean(true);
        final Thread hilo;
        Vivo(String tema, String grupo, Function<String, Pedido> lector) {
            hilo = new Thread(() -> {
                try (var c = new KafkaConsumer<String, String>(Kf.consumo(B, grupo, "read_uncommitted"))) {
                    c.subscribe(List.of(tema));
                    while (seguir.get()) {
                        for (var r : c.poll(Duration.ofMillis(200))) {
                            try {
                                leidos.add(lector.apply(r.value()));
                                bien.incrementAndGet();
                            } catch (RuntimeException e) {
                                fallos.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                            }
                        }
                        c.commitSync();
                    }
                }
            });
            hilo.setDaemon(true);
            hilo.start();
        }
        int total() { return bien.get() + fallos.size(); }
        @Override public void close() throws InterruptedException { seguir.set(false); hilo.join(10_000); }
    }

    // ---------------------------------------------------------------- pasos 3 y 4: esquemas
    @Test
    @Order(1)
    void paso3_losEventosCumplenSuEsquemaYLasVersionesSeDistinguen() throws Exception {
        assertTrue(valida("PedidoConfirmado.v1", Eventos.pedidoV1("pedido-1", "cliente-7", 3980000, "COP")));
        assertTrue(valida("PedidoConfirmado.v2", Eventos.pedidoV2("pedido-1", "cliente-7", 3980000, "COP", "app")));
        assertTrue(valida("PagoAprobado.v1", Eventos.pago("pedido-1", 3980000)));
        assertTrue(valida("InventarioReservado.v1", Eventos.inventario("pedido-1")));
        // los esquemas son cerrados: un evento con un campo que su versión no declara no los cumple
        assertTrue(!valida("PedidoConfirmado.v1", Eventos.pedidoV2("pedido-1", "cliente-7", 1, "COP", "app")));
        assertTrue(!valida("PedidoConfirmado.v2", Eventos.pedidoRenombrado("pedido-1", "cliente-7", 1, "COP")));
        // ningún evento es la entidad interna: ni ids de JPA, ni datos personales
        String e = Eventos.pedidoV1("pedido-1", "cliente-7", 1, "COP");
        for (String prohibido : new String[] {"nombre", "documento", "correo", "version_jpa", "usuario_modifica"}) assertTrue(!e.contains(prohibido));
        Ev.nota("3. los tres eventos validan contra su esquema; cada versión rechaza campos que no declara");
    }

    // ---------------------------------------------------------------- paso 6 y 7
    @Test
    @Order(2)
    void pasos6y7_evolucionCompatibleConVersionesMezcladas() throws Exception {
        Kf.tema("pedidos.eventos", 3);
        String[] viejos = new String[10];
        for (int i = 0; i < 10; i++) viejos[i] = Eventos.pedidoV1("pedido-" + i, "cliente-" + i, 1000 + i, "COP");
        for (int i = 0; i < 10; i++) publicar("pedidos.eventos", "pedido-" + i, viejos[i]);
        try (var v1 = new Vivo("pedidos.eventos", "viejo", Lectores::leerV1); var v2 = new Vivo("pedidos.eventos", "nuevo", Lectores::leerV2)) {
            for (int i = 10; i < 20; i++) publicar("pedidos.eventos", "pedido-" + i, Eventos.pedidoV2("pedido-" + i, "cliente-" + i, 1000 + i, "COP", "app"));
            Kf.esperar(() -> v1.total() == 20 && v2.total() == 20, 30_000, "20 eventos en cada consumidor");
            Ev.nota("7. consumidor VIEJO (solo conoce v1): " + v1.bien.get() + " bien, " + v1.fallos.size() + " fallos sobre 10 eventos v1 + 10 v2");
            Ev.nota("7. consumidor NUEVO (lee v2): " + v2.bien.get() + " bien, " + v2.fallos.size() + " fallos; canal de un evento viejo: " + v2.leidos.stream().filter(p -> p.pedidoId().equals("pedido-3")).findFirst().orElseThrow().canal());
            assertEquals(20, v1.bien.get());
            assertEquals(20, v2.bien.get());
            assertEquals("web", v2.leidos.stream().filter(p -> p.pedidoId().equals("pedido-3")).findFirst().orElseThrow().canal(), "un evento viejo recibe el valor por defecto");
            assertEquals("app", v2.leidos.stream().filter(p -> p.pedidoId().equals("pedido-13")).findFirst().orElseThrow().canal());
        }
    }

    // ---------------------------------------------------------------- paso 8
    @Test
    @Order(3)
    void paso8_cambioIncompatibleYMigracionSinCortes() throws Exception {
        // a) el cambio hecho de golpe rompe al consumidor viejo
        Kf.tema("pedidos.golpe", 3);
        try (var v1 = new Vivo("pedidos.golpe", "viejo", Lectores::leerV1)) {
            for (int i = 0; i < 5; i++) publicar("pedidos.golpe", "pedido-" + i, Eventos.pedidoRenombrado("pedido-" + i, "cliente-1", 100, "COP"));
            Kf.esperar(() -> v1.total() == 5, 30_000, "5 eventos");
            Ev.nota("8a. renombrar totalCentavos -> total de golpe: el consumidor viejo falla " + v1.fallos.size() + " de 5: " + v1.fallos.get(0));
            assertEquals(5, v1.fallos.size());
        }
        // b) expandir -> migrar consumidores -> contraer, con un consumidor vivo en todo momento
        Kf.tema("pedidos.migracion", 3);
        Vivo viejo = new Vivo("pedidos.migracion", "servicio-facturacion", Lectores::leerV1);
        int total = 0;
        for (int i = 0; i < 5; i++, total++) publicar("pedidos.migracion", "p" + total, Eventos.pedidoV1("p" + total, "c", 100, "COP"));
        Kf.esperar(() -> viejo.total() == 5, 30_000, "fase 0");
        for (int i = 0; i < 5; i++, total++) publicar("pedidos.migracion", "p" + total, Eventos.pedidoExpandido("p" + total, "c", 100, "COP"));   // fase 1: los dos nombres
        Kf.esperar(() -> viejo.total() == 10, 30_000, "fase 1");
        assertEquals(10, viejo.bien.get(), "con los dos nombres, el consumidor viejo no nota nada");
        viejo.close();                                                                                                   // despliegue del consumidor migrado, mismo grupo
        try (var nuevo = new Vivo("pedidos.migracion", "servicio-facturacion", Lectores::leerV2)) {
            for (int i = 0; i < 5; i++, total++) publicar("pedidos.migracion", "p" + total, Eventos.pedidoContraido("p" + total, "c", 100, "COP"));   // fase 2: solo el nombre nuevo
            Kf.esperar(() -> nuevo.total() == 5, 30_000, "fase 2");
            Ev.nota("8b. migración: fase 1 (ambos nombres) viejo 10/10 bien; fase 2 (solo total) tras migrar el consumidor: " + nuevo.bien.get() + "/5 bien, 0 fallos en todo el proceso");
            assertEquals(5, nuevo.bien.get());
        }
        assertEquals(0, viejo.fallos.size());
        // y los eventos viejos que siguen en el tema los lee el consumidor nuevo desde el principio
        try (var relectura = new Vivo("pedidos.migracion", "relectura", Lectores::leerV2)) {
            Kf.esperar(() -> relectura.total() == 15, 30_000, "relectura");
            assertEquals(15, relectura.bien.get());
        }
    }

    // ---------------------------------------------------------------- paso 9
    @Test
    @Order(4)
    void paso9_valorNuevoEnUnaEnumeracion() {
        String eur = Eventos.pedidoV1("pedido-1", "cliente-7", 100, "EUR");
        var e = assertThrows(IllegalArgumentException.class, () -> Lectores.leerV1Estricto(eur));
        Pedido p = Lectores.leerV1(eur);
        Ev.nota("9. EUR con enumeración estricta: " + e.getMessage() + "; con lectura tolerante: moneda=" + p.moneda() + " (se procesa y se marca para revisión)");
        assertEquals(Lectores.Moneda.DESCONOCIDA, p.moneda());
    }

    // ---------------------------------------------------------------- paso 10
    @Test
    @Order(5)
    void paso10_registroDeEsquemasRechazaElCambioIncompatible() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        String base = REGISTRO + "/apis/registry/v3/groups/default/artifacts";
        String id = "pedidos.eventos-" + Kf.unico("valor");
        var mapper = Eventos.JSON;
        java.util.function.BiFunction<String, String, String> version = (archivo, v) -> {
            try {
                var o = mapper.createObjectNode();
                o.put("version", v);
                o.putObject("content").put("content", Files.readString(Path.of("esquemas", archivo + ".schema.json"))).put("contentType", "application/json");
                return o.toString();
            } catch (java.io.IOException ex) {
                throw new IllegalStateException(ex);
            }
        };
        java.util.function.BiFunction<String, String, HttpResponse<String>> post = (url, cuerpo) -> {
            try {
                return http.send(HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build(), HttpResponse.BodyHandlers.ofString());
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        };
        var primera = mapper.createObjectNode();
        primera.put("artifactId", id);
        primera.put("artifactType", "JSON");
        primera.set("firstVersion", mapper.readTree(version.apply("PedidoConfirmado.v1", "1")));
        assertEquals(200, post.apply(base, primera.toString()).statusCode());
        assertEquals(204, post.apply(base + "/" + id + "/rules", "{\"ruleType\":\"COMPATIBILITY\",\"config\":\"BACKWARD\"}").statusCode());
        var v2 = post.apply(base + "/" + id + "/versions", version.apply("PedidoConfirmado.v2", "2"));
        var v3 = post.apply(base + "/" + id + "/versions", version.apply("PedidoConfirmado.v3-incompatible", "3"));
        // fase 1 de la migración (expandir) y fase 2 (contraer) sí pasan, porque cada paso es compatible con el anterior
        Ev.nota("10. registro Apicurio 3.3.4, regla COMPATIBILITY=BACKWARD. v1: 200; v2 (campo opcional canal): " + v2.statusCode()
                + "; v3 (renombrar totalCentavos a total): " + v3.statusCode());
        JsonNode rechazo = mapper.readTree(v3.body());
        Files.createDirectories(Path.of("salida"));
        Files.writeString(Path.of("salida", "rechazo-registro.txt"), "HTTP " + v3.statusCode() + "\n" + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rechazo) + "\n");
        Ev.nota("10. rechazo guardado en salida/rechazo-registro.txt: " + rechazo.get("title").asText());
        assertEquals(200, v2.statusCode());
        assertTrue(v3.statusCode() == 409 || v3.statusCode() == 400, "HTTP " + v3.statusCode());
        assertTrue(rechazo.get("detail").asText().contains("Incompatible"));
    }

    // ---------------------------------------------------------------- paso 11: la prueba de contrato del consumidor
    /**
     * Prueba de contrato del consumidor de PedidoConfirmado: toma los eventos que el productor dice publicar (sus
     * fábricas) y comprueba que el consumidor puede leerlos todos. Si el productor renombra un campo, esta prueba falla.
     */
    @Test
    @Order(6)
    void paso11_pruebaDeContratoDelConsumidor() {
        List<String> publicados = List.of(
                Eventos.pedidoV1("pedido-1", "cliente-7", 100, "COP"),
                Eventos.pedidoV2("pedido-1", "cliente-7", 100, "USD", "app"),
                Eventos.pedidoExpandido("pedido-1", "cliente-7", 100, "COP"));
        for (String e : publicados) Lectores.leerV1(e);                       // si alguno no se puede leer, la prueba falla
        Ev.nota("11. contrato: el consumidor v1 lee los " + publicados.size() + " eventos que el productor publica hoy");
        // control negativo: la prueba SÍ detecta el cambio incompatible
        var roto = assertThrows(Lectores.EventoIlegible.class, () -> Lectores.leerV1(Eventos.pedidoRenombrado("pedido-1", "cliente-7", 100, "COP")));
        Ev.nota("11. control negativo: con el productor renombrando el campo, la prueba falla con '" + roto.getMessage() + "'");
    }
}
