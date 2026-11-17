package tienda.eventos;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class ConfiguracionKafka {
    @Bean NewTopic temaEventos() { return TopicBuilder.name(RelevoOutbox.TEMA).partitions(3).replicas(1).build(); }
}
