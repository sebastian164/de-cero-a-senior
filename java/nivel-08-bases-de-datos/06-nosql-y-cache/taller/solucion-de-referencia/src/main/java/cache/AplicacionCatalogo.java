package cache;

import java.time.Duration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@SpringBootApplication
@EnableCaching
public class AplicacionCatalogo {

    /**
     * Estadísticas para medir la tasa de aciertos, y una caché aparte con TTL corto para el paso 2.
     * Spring Data Redis 4 escribe y borra en la caché de forma asíncrona por defecto (con Lettuce): put y evict
     * vuelven antes de que Redis aplique el cambio. immediateWrites() hace que vuelvan cuando ya está aplicado;
     * sin eso, la doble comprobación del bloqueo por clave a veces no ve el valor recién guardado.
     */
    @Bean
    RedisCacheManagerBuilderCustomizer personalizarCaches(RedisConnectionFactory conexiones) {
        return builder -> builder
                .cacheWriter(RedisCacheWriter.create(conexiones, c -> c.immediateWrites().collectStatistics()))
                .enableStatistics()
                .withCacheConfiguration("productos-ttl-corto", RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(1)));
    }
}
