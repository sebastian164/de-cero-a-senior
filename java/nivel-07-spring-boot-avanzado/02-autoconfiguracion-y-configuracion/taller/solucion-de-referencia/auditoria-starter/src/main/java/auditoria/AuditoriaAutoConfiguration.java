package auditoria;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

// Copiado del README tal cual (salvo ser público y el paquete)
@AutoConfiguration
@ConditionalOnClass(ClienteAuditoria.class)
@EnableConfigurationProperties(AuditoriaProperties.class)
class AuditoriaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ClienteAuditoria clienteAuditoria(AuditoriaProperties propiedades) {
        return new ClienteAuditoria(propiedades.url());
    }
}
