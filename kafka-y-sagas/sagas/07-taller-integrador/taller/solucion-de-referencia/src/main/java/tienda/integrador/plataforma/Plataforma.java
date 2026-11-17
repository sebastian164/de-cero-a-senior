package tienda.integrador.plataforma;

import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Lo común a los siete servicios: transacciones, publicador de la tabla de salida y política de errores de los consumidores. */
@Configuration
public class Plataforma {

    public static final String FALLIDOS = "compra.fallidos";

    @Bean
    TransactionTemplate plantilla(PlatformTransactionManager manager) {
        return new TransactionTemplate(manager);
    }

    @Bean
    PublicadorDeSalida publicador(JdbcTemplate jdbc, TransactionTemplate tx, KafkaTemplate<String, String> kafka, @Value("${tienda.muere-al-publicar:0}") long muere) {
        PublicadorDeSalida.crearTabla(jdbc);
        return new PublicadorDeSalida(jdbc, tx, kafka, muere);
    }

    /** Ilegible -> tema de fallidos sin reintentos; fallo pasajero -> reintentos con espera creciente. */
    @Bean
    DefaultErrorHandler manejadorDeErrores(KafkaTemplate<String, String> kafka) {
        var aFallidos = new DeadLetterPublishingRecoverer(kafka, (registro, error) -> new TopicPartition(FALLIDOS, -1));
        var espera = new ExponentialBackOffWithMaxRetries(6);
        espera.setInitialInterval(500);
        espera.setMultiplier(2);
        espera.setMaxInterval(4000);
        var manejador = new DefaultErrorHandler(aFallidos, espera);
        manejador.addNotRetryableExceptions(MensajeIlegible.class);
        return manejador;
    }
}
