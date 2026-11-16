package citas;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class Configuracion {

    @Bean
    Clock reloj() {
        return Clock.systemDefaultZone();
    }

    @Bean
    OpenAPI documentacion() {
        return new OpenAPI().info(new Info().title("API de citas médicas").version("v1, v2"));
    }
}
