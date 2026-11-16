package obs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/** App real en el puerto 42210 (puerto fijo para la prueba). Metrics, sondas, logs JSON con traza. */
class ObservabilidadTest {

    static final String BASE = "http://localhost:42210";
    static final HttpClient http = HttpClient.newHttpClient();
    static ConfigurableApplicationContext ctx;
    static final ByteArrayOutputStream salida = new ByteArrayOutputStream();
    static PrintStream original;

    @BeforeAll
    static void arrancar() {
        original = System.out;
        System.setOut(new PrintStream(salida, true));
        ctx = SpringApplication.run(ObsApplication.class, "--server.port=42210", "--app.destino=" + BASE,
                "--management.opentelemetry.tracing.export.otlp.endpoint=http://localhost:42999/v1/traces");
    }

    @AfterAll
    static void parar() {
        ctx.close();
        System.setOut(original);
    }

    static HttpResponse<String> get(String ruta, String... cabeceras) throws Exception {
        var b = HttpRequest.newBuilder(URI.create(BASE + ruta)).GET();
        if (cabeceras.length > 0) b.header(cabeceras[0], cabeceras[1]);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    static HttpResponse<String> post(String ruta) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(BASE + ruta)).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void sondasDeVidaYDisponibilidadNoDependenDelExterno() throws Exception {
        assertThat(get("/actuator/health/liveness").statusCode()).isEqualTo(200);
        assertThat(get("/actuator/health/readiness").statusCode()).isEqualTo(200);
        assertThat(get("/actuator/health").statusCode()).isEqualTo(200);
        post("/test/externo/caer");
        try {
            assertThat(get("/actuator/health").statusCode()).isEqualTo(503);          // salud global: DOWN
            assertThat(get("/actuator/health/liveness").statusCode()).isEqualTo(200); // la de vida no se entera
            assertThat(get("/actuator/health/readiness").statusCode()).isEqualTo(200);
        } finally {
            post("/test/externo/subir");
        }
    }

    @Test
    void metricasDeNegocioYTecnicasEnPrometheus() throws Exception {
        for (int i = 1; i <= 3; i++) assertThat(get("/a/pedido/" + i).statusCode()).isEqualTo(200);
        String p = get("/actuator/prometheus").body();
        assertThat(p).contains("pedidos_confirmados_total")
                .contains("pedidos_rechazados_total{")
                .contains("pedidos_en_proceso")
                .contains("pagos_cobro_duracion_seconds_count")
                .contains("pagos_cobro_duracion_seconds_bucket{")   // histograma: p95 y p99 se calculan en Prometheus
                .contains("http_server_requests_seconds_bucket");
        var confirmados = p.lines().filter(l -> l.startsWith("pedidos_confirmados_total")).findFirst().orElseThrow();
        assertThat(Double.parseDouble(confirmados.substring(confirmados.lastIndexOf(' ') + 1))).isGreaterThanOrEqualTo(3.0);
        assertThat(p).doesNotContain("pagos_cobro_duracion_seconds{quantile");      // con histograma solo hay buckets
        // solo lo necesario expuesto: nada de env, metrics ni loggers (loggers permite cambiar niveles por POST)
        for (String ruta : new String[] {"/actuator/env", "/actuator/metrics", "/actuator/loggers", "/actuator/beans"})
            assertThat(get(ruta).statusCode()).as(ruta).isEqualTo(404);
        assertThat(post("/actuator/loggers/ROOT").statusCode()).isEqualTo(404);
        assertThat(get("/test/externo/caer").statusCode()).isIn(404, 405);
        // los detalles de salud (rutas de disco, etc.) no se publican
        assertThat(get("/actuator/health").body()).doesNotContain("details").doesNotContain("diskSpace").doesNotContain("path");
    }

    @Test
    void registroJsonConIdentificadorDeTrazaEnAmbosServicios() throws Exception {
        String traza = "4bf92f3577b34da6a3ce929d0e0e4736";
        get("/a/pedido/77", "traceparent", "00-" + traza + "-00f067aa0ba902b7-01");
        Thread.sleep(300);
        var lineas = salida.toString().lines().filter(l -> l.contains(traza)).toList();
        assertThat(lineas).anyMatch(l -> l.contains("Pedido confirmado pedidoId=77"));
        assertThat(lineas).anyMatch(l -> l.contains("Cobrando pedidoId=77"));
        assertThat(lineas).allMatch(l -> l.startsWith("{") && l.contains("\"@timestamp\"") && l.contains("traceId"));
    }

    @Test
    void apagadoOrdenadoTerminaLaPeticionEnCurso() throws Exception {
        // Segunda instancia, para poder pararla sin afectar a las demás pruebas.
        var otra = SpringApplication.run(ObsApplication.class, "--server.port=42214",
                "--management.opentelemetry.tracing.export.otlp.endpoint=http://localhost:42999/v1/traces");
        var futuro = http.sendAsync(HttpRequest.newBuilder(URI.create("http://localhost:42214/lento?ms=2500")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        Thread.sleep(600);
        long t0 = System.nanoTime();
        otra.close();                                    // SIGTERM equivalente: server.shutdown=graceful
        long cierreMs = (System.nanoTime() - t0) / 1_000_000;
        var resp = futuro.get();
        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(resp.body()).isEqualTo("terminada");
        assertThat(cierreMs).as("el cierre espera a la petición en curso").isGreaterThan(1000);
        // y después ya no acepta conexiones
        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class, () ->
                http.send(HttpRequest.newBuilder(URI.create("http://localhost:42214/lento?ms=1")).GET().build(), HttpResponse.BodyHandlers.ofString()));
    }

    @Test
    void elEnvioDeMetricasPorOtlpEstaActivoPorDefectoYSeDesactiva() {
        var base = new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                        org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration.class,
                        org.springframework.boot.micrometer.metrics.autoconfigure.export.otlp.OtlpMetricsExportAutoConfiguration.class,
                        org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration.class));
        base.run(c -> assertThat(c).hasSingleBean(io.micrometer.registry.otlp.OtlpMeterRegistry.class));
        base.withPropertyValues("management.otlp.metrics.export.enabled=false")
                .run(c -> assertThat(c).doesNotHaveBean(io.micrometer.registry.otlp.OtlpMeterRegistry.class));
    }
}
