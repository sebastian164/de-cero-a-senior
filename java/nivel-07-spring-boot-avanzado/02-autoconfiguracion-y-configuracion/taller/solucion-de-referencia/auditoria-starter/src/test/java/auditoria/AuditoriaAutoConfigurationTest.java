package auditoria;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AuditoriaAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AuditoriaAutoConfiguration.class));

    @Test
    void creaElClienteConLaUrlConfigurada() {
        runner.withPropertyValues("auditoria.url=http://auditoria.interna").run(ctx ->
                assertThat(ctx.getBean(ClienteAuditoria.class).url()).isEqualTo("http://auditoria.interna"));
    }

    @Test
    void seRetiraSiLaAplicacionDeclaraElSuyo() {
        runner.withBean("propio", ClienteAuditoria.class, () -> new ClienteAuditoria("propia")).run(ctx -> {
            assertThat(ctx).hasSingleBean(ClienteAuditoria.class);
            assertThat(ctx.getBean(ClienteAuditoria.class).url()).isEqualTo("propia");
        });
    }
}
