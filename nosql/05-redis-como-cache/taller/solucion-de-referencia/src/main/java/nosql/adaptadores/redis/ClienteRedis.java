package nosql.adaptadores.redis;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import java.time.Duration;

/** Fábrica de clientes de Lettuce: con los valores por defecto y con tiempos de espera cortos que fallan rápido. */
public final class ClienteRedis {
    private ClienteRedis() {}

    public static RedisClient porDefecto(String url) { return RedisClient.create(url); }

    public static RedisClient conTiemposCortos(String url, Duration espera) {
        RedisClient c = RedisClient.create(url);
        c.setOptions(ClientOptions.builder()
                .socketOptions(SocketOptions.builder().connectTimeout(espera).build())
                .timeoutOptions(TimeoutOptions.enabled(espera))
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .build());
        return c;
    }
}
