package pruebas;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.util.backoff.FixedBackOff;

/** Paso 8: el mismo listener con reintentos bloqueantes y con reintentos no bloqueantes (temas de reintento). */
public class Reintentos {

    public static final List<String> ORDEN = new CopyOnWriteArrayList<>();
    static final AtomicInteger INTENTOS = new AtomicInteger();

    static void tratar(String mensaje) {
        if (mensaje.equals("a1-primero") && INTENTOS.getAndIncrement() == 0) throw new IllegalStateException("fallo pasajero del primero");
        ORDEN.add(mensaje);
    }

    @Configuration
    @EnableAutoConfiguration
    public static class Bloqueante {
        @Bean
        DefaultErrorHandler manejador() {
            return new DefaultErrorHandler(new FixedBackOff(1500, 3));
        }

        @KafkaListener(id = "bloqueante", topics = "reintentos-bloqueantes", groupId = "bloqueante")
        void escuchar(String mensaje) {
            tratar(mensaje);
        }
    }

    @Configuration
    @EnableAutoConfiguration
    public static class NoBloqueante {
        @RetryableTopic(attempts = "3", backOff = @BackOff(delay = 1500), numPartitions = "1")
        @KafkaListener(id = "noBloqueante", topics = "reintentos-no-bloqueantes", groupId = "noBloqueante")
        void escuchar(String mensaje) {
            tratar(mensaje);
        }
    }
}
