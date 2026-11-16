package obs;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Dependencia externa simulada. Si cae, /actuator/health pasa a DOWN, pero la sonda de vida no debe cambiar. */
@Component("externo")
class ExternoSalud implements HealthIndicator {
    static final AtomicBoolean arriba = new AtomicBoolean(true);

    @Override
    public Health health() { return arriba.get() ? Health.up().build() : Health.down().withDetail("motivo", "externo caído").build(); }
}
