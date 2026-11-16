package tienda;

import auditoria.ClienteAuditoria;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import tienda.antes.PagosConValue;
import tienda.config.PagosProperties;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfiguracionTest {

    static ConfigurableApplicationContext arrancar(String... args) {
        List<String> todos = new ArrayList<>(List.of("--server.port=0", "--PAGOS_CLAVE_API=sk_test_123"));
        todos.addAll(List.of(args));
        return new SpringApplicationBuilder(TiendaApplication.class).run(todos.toArray(String[]::new));
    }

    @Test
    void lasPropiedadesTipadasSeEnlazan() {
        try (var ctx = arrancar()) {
            var p = ctx.getBean(PagosProperties.class);
            assertThat(p.claveApi()).isEqualTo("sk_test_123");
            assertThat(p.tiempoLimite()).isEqualTo(Duration.ofSeconds(3));
            assertThat(p.reintentos()).isEqualTo(2);
            assertThat(ctx.getBean(ClienteAuditoria.class).url()).isEqualTo("http://auditoria.local");   // el starter propio
        }
    }

    @Test
    void unValorInvalidoImpideElArranque() {
        assertThatThrownBy(() -> arrancar("--pagos.reintentos=9"))
                .rootCause().hasMessageContaining("Binding validation errors on pagos")
                .hasMessageContaining("reintentos");
    }

    @Test
    void conValueElMismoValorInvalidoPasaSinAviso() {
        // Sin la clase validada, @Value acepta el 9: se demuestra excluyendo PagosProperties del arranque
        try (var ctx = new SpringApplicationBuilder(SoloValue.class).web(org.springframework.boot.WebApplicationType.NONE)
                .run("--PAGOS_CLAVE_API=x", "--pagos.reintentos=9")) {
            assertThat(ctx.getBean(PagosConValue.class).reintentos).isEqualTo(9);
        }
    }

    @org.springframework.context.annotation.Configuration
    @org.springframework.context.annotation.Import(PagosConValue.class)
    static class SoloValue { }

    @Test
    void sinLaClaveDelEntornoLaAplicacionNoArranca() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(TiendaApplication.class).run("--server.port=0"))
                .rootCause().hasMessageContaining("Could not resolve placeholder 'PAGOS_CLAVE_API'");
    }

    /** Precedencia real: perfil + variable de entorno en un proceso aparte. */
    @Test
    void perfilesYVariableDeEntorno() throws Exception {
        assertThat(leerEnProceso(Map.of("SPRING_PROFILES_ACTIVE", "dev"))).contains("reintentos=0");
        assertThat(leerEnProceso(Map.of("SPRING_PROFILES_ACTIVE", "test"))).contains("reintentos=1");
        assertThat(leerEnProceso(Map.of("SPRING_PROFILES_ACTIVE", "prod"))).contains("reintentos=3", "tiempoLimite=PT2S");
        // la variable de entorno gana sobre application-prod.yml
        assertThat(leerEnProceso(Map.of("SPRING_PROFILES_ACTIVE", "prod", "PAGOS_REINTENTOS", "5"))).contains("reintentos=5");
        // y el argumento de línea de comandos gana sobre la variable de entorno
        assertThat(leerEnProceso(Map.of("SPRING_PROFILES_ACTIVE", "prod", "PAGOS_REINTENTOS", "5"), "--pagos.reintentos=4")).contains("reintentos=4");
    }

    static String leerEnProceso(Map<String, String> entorno, String... args) throws Exception {
        List<String> cmd = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), "tienda.MostrarConfig"));
        cmd.addAll(List.of(args));
        var pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        pb.environment().putAll(entorno);
        pb.environment().put("PAGOS_CLAVE_API", "sk_desde_entorno");
        Process p = pb.start();
        String salida = new String(p.getInputStream().readAllBytes());
        p.waitFor(60, TimeUnit.SECONDS);
        return salida.lines().filter(l -> l.startsWith("CONFIG ")).findFirst().orElse(salida);
    }

    static String resultado(ConfigurableApplicationContext ctx, String sufijo) {
        var porFuente = ConditionEvaluationReport.get(ctx.getBeanFactory()).getConditionAndOutcomesBySource();
        var entrada = porFuente.entrySet().stream().filter(e -> e.getKey().endsWith(sufijo)).findFirst().orElseThrow(
                () -> new AssertionError("no está en el reporte: " + sufijo));
        var r = entrada.getValue();
        String mensaje = (r.isFullMatch() ? "SÍ  " : "NO  ") + sufijo + " -> "
                + java.util.stream.StreamSupport.stream(r.spliterator(), false)
                        .filter(c -> c.getOutcome().isMatch() == r.isFullMatch()).findFirst().orElseThrow().getOutcome().getMessage();
        System.out.println(mensaje);
        return mensaje;
    }

    @Test
    void elJsonMapperPropioRetiraAlAutoconfigurado() {
        try (var ctx = arrancar()) {
            assertThat(ctx.getBeansOfType(JsonMapper.class)).containsOnlyKeys("miJsonMapper");
            assertThat(resultado(ctx, "JacksonAutoConfiguration#jacksonJsonMapper"))
                    .startsWith("NO").contains("found beans of type 'tools.jackson.databind.JsonMapper' miJsonMapper".replace("databind.JsonMapper", "databind.json.JsonMapper"));
        }
    }

    /** En Boot 4 la autoconfiguración pregunta por JsonMapper: un bean declarado como ObjectMapper no la retira. */
    @Test
    void unObjectMapperDeclaradoComoTalNoLaRetira() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                        org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration.class))
                .withBean("miObjectMapper", tools.jackson.databind.ObjectMapper.class, tools.jackson.databind.ObjectMapper::new)
                .run(ctx -> assertThat(ctx.getBeansOfType(tools.jackson.databind.ObjectMapper.class))
                        .containsOnlyKeys("miObjectMapper", "jacksonJsonMapper"));
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                        org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration.class))
                .withBean("miJsonMapper", JsonMapper.class, JsonMapper::new)
                .run(ctx -> assertThat(ctx.getBeansOfType(tools.jackson.databind.ObjectMapper.class)).containsOnlyKeys("miJsonMapper"));
    }

    /** Tres autoconfiguraciones que sí se aplicaron y tres que no, con su razón. */
    @Test
    void reporteDeCondiciones() {
        try (var ctx = arrancar()) {
            assertThat(resultado(ctx, "auditoria.AuditoriaAutoConfiguration")).startsWith("SÍ");
            assertThat(resultado(ctx, ".DispatcherServletAutoConfiguration")).startsWith("SÍ");
            assertThat(resultado(ctx, ".ValidationAutoConfiguration#defaultValidator")).startsWith("SÍ");
            assertThat(resultado(ctx, "AopAutoConfiguration$AspectJAutoProxyingConfiguration")).startsWith("NO");
            assertThat(resultado(ctx, ".AuditAutoConfiguration")).startsWith("NO");
            assertThat(resultado(ctx, "TaskSchedulingConfigurations$TaskSchedulerConfiguration")).startsWith("NO");
        }
    }
}
