package obs;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Servicio B: cobra. La latencia artificial se activa con app.latencia-cobro-ms (el que investiga no lo sabe). */
@Component
class Pasarela {
    private final Timer tiempoDeCobro;
    private final ObservationRegistry observaciones;
    private final long latenciaMs;

    Pasarela(MeterRegistry registro, ObservationRegistry observaciones, @Value("${app.latencia-cobro-ms:0}") long latenciaMs) {
        this.tiempoDeCobro = Timer.builder("pagos.cobro.duracion")
                .description("Duración del cobro en la pasarela")
                .publishPercentiles(0.95, 0.99)
                .publishPercentileHistogram()
                .register(registro);
        this.observaciones = observaciones;
        this.latenciaMs = latenciaMs;
    }

    void cobrar(long pedidoId) {
        Observation.createNotStarted("pagos.cobro", observaciones).observe(() -> tiempoDeCobro.record(() -> {
            try {
                Thread.sleep(5 + latenciaMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
    }
}
