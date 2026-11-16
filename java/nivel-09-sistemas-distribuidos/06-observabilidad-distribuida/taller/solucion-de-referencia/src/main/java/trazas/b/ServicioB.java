package trazas.b;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.Map;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Servicio B: inventario (HTTP, con retraso configurable) y consumidor de Kafka con dos pasos asíncronos. */
@SpringBootApplication
@EnableAsync
@RestController
public class ServicioB {

    static final Logger log = LoggerFactory.getLogger(ServicioB.class);

    private volatile long retrasoMs = 0;
    private final Notificaciones notificaciones;

    ServicioB(Notificaciones notificaciones) { this.notificaciones = notificaciones; }

    @GetMapping("/inventario/{id}")
    Map<String, Object> inventario(@PathVariable long id) throws InterruptedException {
        if (retrasoMs > 0) {
            Thread.sleep(retrasoMs);
        }
        log.info("inventario consultado id={}", id);
        return Map.of("id", id, "existencias", 7);
    }

    /** Para el paso 6: introduce latencia en B. */
    @PostMapping("/control")
    String control(@RequestParam long retraso) {
        this.retrasoMs = retraso;
        return "retraso=" + retraso;
    }

    @KafkaListener(topics = "pedidos.creados", groupId = "${grupo.servicio-b:servicio-b}")
    void alRecibir(String mensaje) {
        log.info("evento recibido {}", mensaje);
        notificaciones.sinContexto(mensaje);
        notificaciones.conContexto(mensaje);
    }

    /** Dos ejecutores: uno cualquiera (rompe la propagación) y uno que copia el contexto de la traza al hilo. */
    @Component
    static class Notificaciones {
        private final ObservationRegistry registro;
        Notificaciones(ObservationRegistry registro) { this.registro = registro; }

        @Async("sinContexto")
        void sinContexto(String mensaje) {
            Observation.createNotStarted("notificar.roto", registro).observe(() -> log.info("notificar ROTO {}", mensaje));
        }

        @Async("conContexto")
        void conContexto(String mensaje) {
            Observation.createNotStarted("notificar.arreglado", registro).observe(() -> log.info("notificar ARREGLADO {}", mensaje));
        }
    }

    @Bean(name = "sinContexto")
    Executor sinContexto() {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(2);
        e.setThreadNamePrefix("sin-contexto-");
        e.initialize();
        return e;
    }

    @Bean(name = "conContexto")
    Executor conContexto() {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(2);
        e.setThreadNamePrefix("con-contexto-");
        e.setTaskDecorator(new org.springframework.core.task.support.ContextPropagatingTaskDecorator());
        e.initialize();
        return e;
    }

    public static void main(String[] args) {
        SpringApplication.run(ServicioB.class, args);
    }
}
