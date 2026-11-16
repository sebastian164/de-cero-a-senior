package contenedor.pagos;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(PagosProperties.class)
class PagosConfig {

    @Bean
    @ConditionalOnProperty(name = "pagos.proveedor", havingValue = "stripe")
    PasarelaPagos pasarelaStripe(PagosProperties propiedades) {
        return new PasarelaStripe(propiedades.claveApi());
    }

    @Bean
    @ConditionalOnProperty(name = "pagos.proveedor", havingValue = "simulada", matchIfMissing = true)
    PasarelaPagos pasarelaSimulada() {
        return new PasarelaSimulada();
    }

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
