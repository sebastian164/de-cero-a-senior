package tienda.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Bean propio: la autoconfiguración de Jackson debe retirarse (@ConditionalOnMissingBean). */
@Configuration
class JsonConfig {
    @Bean
    JsonMapper miJsonMapper() {
        return JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
    }
}
