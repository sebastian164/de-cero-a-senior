package inventario.configuracion;

import inventario.aplicacion.EventoInvalido;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

// La política de errores, en un solo lugar (README de Kafka 06, "Después")
@Configuration
class ErroresDeMensajeria {

    @Bean
    DefaultErrorHandler manejadorDeErrores(KafkaTemplate<String, String> kafka) {
        var aFallidos = new DeadLetterPublishingRecoverer(kafka,
                (registro, error) -> new TopicPartition("pedidos.fallidos", -1));   // publica en el tema de fallidos (partición a elegir por Kafka)
        var espera = new ExponentialBackOffWithMaxRetries(8);               // ocho reintentos con espera creciente, hasta 4 s: cubre ~23 s de caída
        espera.setInitialInterval(500);
        espera.setMultiplier(2);
        espera.setMaxInterval(4000);

        var manejador = new DefaultErrorHandler(aFallidos, espera);
        manejador.addNotRetryableExceptions(EventoInvalido.class);          // defectuoso: directo a fallidos
        return manejador;
    }
}
