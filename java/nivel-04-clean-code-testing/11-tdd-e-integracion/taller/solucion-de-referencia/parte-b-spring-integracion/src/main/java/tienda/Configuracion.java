package tienda;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class Configuracion {

    // Lección 08, "Aplicación profesional": el reloj como bean, tal cual.
    @Bean Clock clock() { return Clock.systemDefaultZone(); }
}
