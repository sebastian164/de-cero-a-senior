package observabilidad;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import soporte.Entorno;
import soporte.Readme;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Taller n9-06: dos servicios Spring Boot 4.1.1 + Kafka 4.3.1 + Grafana OTel-LGTM (Tempo, Prometheus) reales. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ObservabilidadTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String README = "java/nivel-09-sistemas-distribuidos/06-observabilidad-distribuida";
    static final Path DIR = Path.of("target/servicios");
    static Process a, b, aClaveVieja, aSinKafka, bSinKafka;

    /** El YAML del README con dos cambios mínimos: el colector (otel-collector → localhost:43318) y el muestreo (0,1 → 1,0). */
    static String yamlDelReadme() {
        return Readme.bloques(README, "yaml").get(0)
                .replace("http://otel-collector:4318", "http://localhost:43318")
                .replace("probability: 0.1", "probability: 1.0");
    }

    @BeforeAll
    static void arrancar() throws Exception {
        Files.createDirectories(DIR);
        String original = yamlDelReadme();
        // El README (ya corregido) usa la clave de Spring Boot 4: management.opentelemetry.tracing.export.otlp.endpoint.
        assertThat(original).contains("opentelemetry.tracing.export.otlp.endpoint:");
        Files.writeString(DIR.resolve("readme-corregido.yaml"), original);
        // La clave que decía el README antes de la corrección (ya no existe en Spring Boot 4): no exporta ninguna traza.
        Files.writeString(DIR.resolve("readme-tal-cual.yaml"),
                original.replace("opentelemetry.tracing.export.otlp.endpoint:", "otlp.tracing.endpoint:"));
        b = Servicios.lanzar("trazas.b.ServicioB", "servicio-b", 43062, DIR.resolve("readme-corregido.yaml"), DIR.resolve("b.log"));
        a = Servicios.lanzar("trazas.a.ServicioA", "servicio-a", 43061, DIR.resolve("readme-corregido.yaml"), DIR.resolve("a.log"));
        aClaveVieja = Servicios.lanzar("trazas.a.ServicioA", "servicio-a-clave-vieja", 43063, DIR.resolve("readme-tal-cual.yaml"), DIR.resolve("a-vieja.log"));
    }

    @AfterAll
    static void parar() throws Exception {
        Servicios.parar(aSinKafka);
        Servicios.parar(bSinKafka);
        Servicios.parar(aClaveVieja);
        Servicios.parar(a);
        Servicios.parar(b);
    }

    // ---------- helpers de Tempo ----------
    /** Espera a que Tempo tenga la traza con al menos los nombres de tramo pedidos. */
    static JsonNode traza(String traceId, Set<String> nombresEsperados, Duration plazo) throws Exception {
        long fin = System.nanoTime() + plazo.toNanos();
        JsonNode ultimo = null;
        while (System.nanoTime() < fin) {
            var r = Servicios.HTTP.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(Entorno.TEMPO + "/api/traces/" + traceId))
                    .header("Accept", "application/json").build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 200) {
                ultimo = JSON.readTree(r.body());
                var servicios = tramos(ultimo).stream().map(Tramo::servicio).toList();
                if (nombres(ultimo).containsAll(nombresEsperados) && servicios.contains("servicio-a") && servicios.contains("servicio-b")) {
                    return ultimo;
                }
            }
            Thread.sleep(1000);
        }
        return ultimo;
    }

    record Tramo(String servicio, String nombre, long ms) { }

    static List<Tramo> tramos(JsonNode traza) {
        List<Tramo> r = new ArrayList<>();
        if (traza == null) return r;
        JsonNode lotes = traza.has("batches") ? traza.get("batches") : traza.get("resourceSpans");
        for (JsonNode lote : lotes) {
            String servicio = "?";
            for (JsonNode at : lote.get("resource").get("attributes")) {
                if (at.get("key").asString().equals("service.name")) servicio = at.get("value").get("stringValue").asString();
            }
            JsonNode scopes = lote.has("scopeSpans") ? lote.get("scopeSpans") : lote.get("instrumentationLibrarySpans");
            for (JsonNode s : scopes) {
                for (JsonNode sp : s.get("spans")) {
                    long ini = Long.parseLong(sp.get("startTimeUnixNano").asString()), fin = Long.parseLong(sp.get("endTimeUnixNano").asString());
                    r.add(new Tramo(servicio, sp.get("name").asString(), (fin - ini) / 1_000_000));
                }
            }
        }
        return r;
    }

    static Set<String> nombres(JsonNode traza) {
        Set<String> n = new TreeSet<>();
        tramos(traza).forEach(t -> n.add(t.nombre()));
        return n;
    }

    // ---------- 1 y 2. Una sola traza a través de HTTP y Kafka ----------
    @Test @Order(1)
    void paso1_2_una_peticion_http_y_un_mensaje_de_kafka_quedan_en_una_sola_traza_y_el_log_lleva_el_identificador() throws Exception {
        var r = Servicios.get("http://localhost:43061/pedidos/42");
        assertThat(r.statusCode()).isEqualTo(200);
        String traceId = JSON.readTree(r.body()).get("traceId").asString();
        assertThat(traceId).hasSize(32);

        JsonNode t = traza(traceId, Set.of("notificar.arreglado"), Duration.ofSeconds(45));
        List<Tramo> tramos = tramos(t);
        System.out.println("Traza " + traceId + ":");
        tramos.forEach(x -> System.out.println("  " + x.servicio() + " | " + x.nombre() + " | " + x.ms() + " ms"));
        assertThat(tramos).extracting(Tramo::servicio).contains("servicio-a", "servicio-b");
        assertThat(tramos).extracting(Tramo::nombre).anyMatch(n -> n.contains("pedidos/{id}"))      // servidor en A
                .anyMatch(n -> n.contains("inventario/{id}"))                                      // servidor/cliente hacia B
                .anyMatch(n -> n.contains("pedidos.creados"));                                     // envío y recepción de Kafka
        assertThat(tramos.stream().filter(x -> x.nombre().contains("pedidos.creados")).map(Tramo::servicio).toList())
                .as("el tramo de Kafka existe en el productor (A) y en el consumidor (B)").contains("servicio-a", "servicio-b");
        Files.writeString(DIR.resolve("traza-http-kafka.txt"), traceId + "\n" + tramos.stream()
                .map(x -> x.servicio() + " | " + x.nombre() + " | " + x.ms() + " ms").reduce("", (p, q) -> p + q + "\n"));

        // Correlación: el identificador aparece en cada línea de registro, de ambos servicios, con el patrón del README.
        String logB = Servicios.leer(DIR.resolve("b.log"));
        assertThat(logB).contains("[servicio-b," + traceId + ",");
        assertThat(Servicios.leer(DIR.resolve("a.log")).contains("[servicio-a,")).isTrue();
    }

    // ---------- 3. @Async rompe la propagación; el decorador la arregla ----------
    @Test @Order(2)
    void paso3_un_async_con_un_ejecutor_normal_corta_la_traza_y_un_ejecutor_con_decorador_de_contexto_la_conserva() throws Exception {
        var r = Servicios.get("http://localhost:43061/pedidos/43");
        String traceId = JSON.readTree(r.body()).get("traceId").asString();
        JsonNode t = traza(traceId, Set.of("notificar.arreglado"), Duration.ofSeconds(45));
        Set<String> n = nombres(t);
        assertThat(n).contains("notificar.arreglado").doesNotContain("notificar.roto");

        Thread.sleep(2000);
        String logB = Servicios.leer(DIR.resolve("b.log"));
        String lineaRota = logB.lines().filter(l -> l.contains("notificar ROTO") && l.contains("\"pedidoId\":43")).findFirst().orElseThrow();
        String lineaBuena = logB.lines().filter(l -> l.contains("notificar ARREGLADO") && l.contains("\"pedidoId\":43")).findFirst().orElseThrow();
        System.out.println(lineaRota + "\n" + lineaBuena);
        assertThat(lineaRota).as("sin contexto: el identificador de traza falta (o es otro)").doesNotContain(traceId);
        assertThat(lineaBuena).contains(traceId);
        // El tramo "notificar.roto" existe, pero en OTRA traza (una raíz huérfana): la traza de la petición se cortó.
    }

    // ---------- La clave del README (management.otlp.tracing.endpoint) ya no existe en Spring Boot 4 ----------
    @Test @Order(3)
    void hallazgo_con_la_clave_del_readme_sin_corregir_no_se_exporta_ninguna_traza() throws Exception {
        var r = Servicios.get("http://localhost:43063/pedidos/44");
        assertThat(r.statusCode()).isEqualTo(200);
        String traceId = JSON.readTree(r.body()).get("traceId").asString();
        assertThat(traceId).as("la traza existe en memoria (el muestreo sí se aplica)").hasSize(32);
        Thread.sleep(12_000);                                      // más que el plazo de exportación por lotes
        var tempo = Servicios.HTTP.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(Entorno.TEMPO + "/api/traces/" + traceId)).build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        // B (bien configurado) sí exportó sus tramos de esa traza; A, con la clave del README, no exportó ninguno.
        assertThat(tempo.statusCode()).isEqualTo(200);
        List<String> servicios = tramos(JSON.readTree(tempo.body())).stream().map(Tramo::servicio).distinct().toList();
        assertThat(servicios).contains("servicio-b").doesNotContain("servicio-a-clave-vieja");
    }

    // ---------- El README dice que los listeners de Kafka propagan solos: ¿también sin observation-enabled? ----------
    @Test @Order(3)
    void hallazgo_sin_las_dos_propiedades_de_observacion_de_kafka_el_tramo_de_kafka_no_aparece_en_la_traza() throws Exception {
        String sinObs[] = {"--spring.kafka.listener.observation-enabled=false", "--spring.kafka.template.observation-enabled=false"};
        List<String> bArgs = new ArrayList<>(List.of(sinObs)); bArgs.add("--grupo.servicio-b=servicio-b-sin-observacion");
        List<String> aArgs = new ArrayList<>(List.of(sinObs)); aArgs.add("--servicio-b.url=http://localhost:43065");
        bSinKafka = Servicios.lanzar("trazas.b.ServicioB", "servicio-b", 43065, DIR.resolve("readme-corregido.yaml"), DIR.resolve("b-sin-kafka.log"), bArgs.toArray(String[]::new));
        aSinKafka = Servicios.lanzar("trazas.a.ServicioA", "servicio-a", 43064, DIR.resolve("readme-corregido.yaml"), DIR.resolve("a-sin-kafka.log"), aArgs.toArray(String[]::new));
        var r = Servicios.get("http://localhost:43064/pedidos/45");
        assertThat(r.statusCode()).isEqualTo(200);
        String traceId = JSON.readTree(r.body()).get("traceId").asString();
        Thread.sleep(12_000);                                      // plazo de exportación por lotes + ingesta de Tempo
        JsonNode t = traza(traceId, Set.of(), Duration.ofSeconds(30));
        List<Tramo> tramos = tramos(t);
        System.out.println("Traza SIN observación de Kafka " + traceId + ":");
        tramos.forEach(x -> System.out.println("  " + x.servicio() + " | " + x.nombre() + " | " + x.ms() + " ms"));
        Files.writeString(DIR.resolve("traza-sin-observacion-kafka.txt"), tramos.stream().map(x -> x.servicio() + " | " + x.nombre() + " | " + x.ms() + " ms\n").reduce("", String::concat));
        assertThat(tramos).extracting(Tramo::servicio).contains("servicio-a", "servicio-b");        // el salto HTTP sí
        assertThat(tramos).extracting(Tramo::nombre).noneMatch(n -> n.contains("pedidos.creados"));  // pero Kafka no deja tramos
        Servicios.parar(aSinKafka); Servicios.parar(bSinKafka);
    }

    // ---------- 4 y 6. SLI, SLO, presupuesto de error y un incidente real ----------
    record Medida(int total, int buenas, double p95) { double sli() { return 100.0 * buenas / total; } }

    static Medida cargar(int n) throws Exception {
        List<Long> ms = new ArrayList<>();
        int buenas = 0;
        for (int i = 0; i < n; i++) {
            long t0 = System.nanoTime();
            var r = Servicios.get("http://localhost:43061/pedidos/" + (1000 + i));
            long d = (System.nanoTime() - t0) / 1_000_000;
            ms.add(d);
            if (r.statusCode() == 200 && d < 300) buenas++;                 // SLI: correcta y en menos de 300 ms
        }
        ms.sort(Long::compare);
        return new Medida(n, buenas, ms.get((int) Math.ceil(n * 0.95) - 1));
    }

    @Test @Order(4)
    void paso4_y_6_el_sli_se_mide_el_presupuesto_se_calcula_y_un_incidente_de_latencia_en_b_se_localiza_con_la_traza() throws Exception {
        // Cálculo del presupuesto: SLO 99,9 % en 30 días.
        double minutosDelMes = 30 * 24 * 60;
        double presupuestoMin = minutosDelMes * (1 - 0.999);
        assertThat(presupuestoMin).isCloseTo(43.2, org.assertj.core.data.Offset.offset(0.001));

        Medida normal = cargar(60);
        System.out.printf(Locale.ROOT, "NORMAL: SLI=%.1f%% p95=%d ms%n", normal.sli(), (long) normal.p95());
        assertThat(normal.sli()).isGreaterThanOrEqualTo(95.0);

        // Incidente: B tarda 800 ms.
        Servicios.post("http://localhost:43062/control?retraso=800");
        Medida incidente = cargar(20);
        System.out.printf(Locale.ROOT, "INCIDENTE: SLI=%.1f%% p95=%d ms%n", incidente.sli(), (long) incidente.p95());
        assertThat(incidente.sli()).isLessThan(5.0);
        assertThat(incidente.p95()).isGreaterThan(750);

        // Velocidad de consumo del presupuesto: con SLI 0 % y SLO 99,9 % se consume 1000 veces más rápido de lo sostenible.
        double tasaDeConsumo = (100 - incidente.sli()) / (100 - 99.9);
        double horasHastaAgotar = (30 * 24) / tasaDeConsumo;
        System.out.printf(Locale.ROOT, "Tasa de consumo: %.0fx; presupuesto agotado en %.2f horas%n", tasaDeConsumo, horasHastaAgotar);

        // Localización con la traza: de una petición lenta, ¿qué tramo concentra el tiempo?
        var lenta = Servicios.get("http://localhost:43061/pedidos/9999");
        String traceId = JSON.readTree(lenta.body()).get("traceId").asString();
        List<Tramo> tramos = tramos(traza(traceId, Set.of("notificar.arreglado"), Duration.ofSeconds(45)));
        Tramo masLento = tramos.stream().filter(x -> x.nombre().contains("inventario")).filter(x -> x.servicio().equals("servicio-b"))
                .findFirst().orElseThrow();
        Tramo total = tramos.stream().filter(x -> x.servicio().equals("servicio-a") && x.nombre().contains("pedidos/{id}")).findFirst().orElseThrow();
        System.out.println("Incidente: tramo del servidor en A " + total.ms() + " ms; tramo de B " + masLento.nombre() + " " + masLento.ms() + " ms");
        assertThat(masLento.ms()).isGreaterThanOrEqualTo(780);
        assertThat(masLento.ms() * 1.0 / total.ms()).as("B explica casi todo el tiempo de A").isGreaterThan(0.9);
        Files.writeString(DIR.resolve("incidente.txt"), String.format(Locale.ROOT,
                "normal: SLI=%.1f%% p95=%d ms%nincidente: SLI=%.1f%% p95=%d ms%ntasa de consumo: %.0fx, agotado en %.2f h%ntraza %s: A=%d ms, B(%s)=%d ms%n",
                normal.sli(), (long) normal.p95(), incidente.sli(), (long) incidente.p95(), tasaDeConsumo, horasHastaAgotar,
                traceId, total.ms(), masLento.nombre(), masLento.ms()));

        // Mitigación y recuperación.
        Servicios.post("http://localhost:43062/control?retraso=0");
        Medida recuperado = cargar(30);
        System.out.printf(Locale.ROOT, "RECUPERADO: SLI=%.1f%% p95=%d ms%n", recuperado.sli(), (long) recuperado.p95());
        assertThat(recuperado.sli()).isGreaterThanOrEqualTo(95.0);
    }

    // ---------- 5. Dos alertas por síntomas con su procedimiento ----------
    static String ejecutar(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String salida = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(p.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(p.exitValue()).as(salida).isZero();
        return salida;
    }

    @Test @Order(5)
    @SuppressWarnings("unchecked")
    void paso5_las_alertas_son_validas_se_disparan_con_series_sinteticas_y_sus_expresiones_funcionan_sobre_las_metricas_reales() throws Exception {
        Path obs = Path.of("infra").toAbsolutePath().normalize();
        String c = "lgtm-n9-06";
        ejecutar("docker", "exec", c, "mkdir", "-p", "/tmp/alertas");
        ejecutar("docker", "cp", obs.resolve("alertas.yml").toString(), c + ":/tmp/alertas/alertas-curso.yml");
        ejecutar("docker", "cp", obs.resolve("alertas_test.yml").toString(), c + ":/tmp/alertas/alertas_test.yml");
        String check = ejecutar("docker", "exec", c, "/otel-lgtm/prometheus/promtool", "check", "rules", "/tmp/alertas/alertas-curso.yml");
        System.out.println(check);
        assertThat(check).contains("SUCCESS: 2 rules found");
        String prueba = ejecutar("docker", "exec", "-w", "/tmp/alertas", c, "/otel-lgtm/prometheus/promtool", "test", "rules", "alertas_test.yml");
        System.out.println(prueba);
        assertThat(prueba).contains("SUCCESS");

        // Las mismas expresiones, contra las métricas reales que dejó el incidente del paso 4.
        var doc = (Map<String, Object>) new org.yaml.snakeyaml.Yaml().load(Files.readString(obs.resolve("alertas.yml")));
        var reglas = (List<Map<String, Object>>) ((Map<String, Object>) ((List<Object>) doc.get("groups")).get(0)).get("rules");
        Thread.sleep(15_000);                                       // un par de exportaciones de métricas (cada 5 s)
        for (var regla : reglas) {
            assertThat(regla.get("annotations").toString()).contains("runbook");
            String expr = ((String) regla.get("expr")).replace("[5m]", "[30m]");   // ventana ampliada: la prueba dura minutos
            var r = Servicios.HTTP.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(Entorno.PROMETHEUS + "/api/v1/query?query="
                    + URLEncoder.encode(expr, StandardCharsets.UTF_8))).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            JsonNode res = JSON.readTree(r.body()).get("data").get("result");
            System.out.println(regla.get("alert") + " sobre datos reales -> " + res);
            if (regla.get("alert").equals("ServicioALentitudAlta")) {
                assertThat(res.size()).as("el incidente de latencia dispara la alerta de lentitud").isEqualTo(1);
            } else {
                assertThat(res.size()).as("no hubo errores 5xx: la alerta de errores no se dispara").isZero();
            }
        }
    }
}
