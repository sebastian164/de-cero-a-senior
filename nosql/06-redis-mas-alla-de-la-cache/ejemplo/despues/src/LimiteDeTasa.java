import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.UUID;

/**
 * Como mucho 5 peticiones en cualquier segundo, por cliente: una ventana deslizante en un conjunto ordenado.
 * Cada petición aceptada es un miembro cuya puntuación es su instante. Todo ocurre en un script de Lua,
 * que Redis ejecuta de una vez: no hay un "entre" en el que otra petición o una caída se cuele.
 */
final class LimiteDeTasa {

    private static final int LIMITE = 5;
    private static final long VENTANA_MS = 1_000;

    private static final String SCRIPT = """
            local t = redis.call('TIME')
            local ahora = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
            local ventana = tonumber(ARGV[1])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ahora - ventana)
            if redis.call('ZCARD', KEYS[1]) < tonumber(ARGV[2]) then
              redis.call('ZADD', KEYS[1], ahora, ARGV[3])
              redis.call('PEXPIRE', KEYS[1], ventana)
              return 1
            end
            return 0
            """;

    private final RedisCommands<String, String> redis;

    LimiteDeTasa(RedisCommands<String, String> redis) {
        this.redis = redis;
    }

    boolean permitir(String cliente) {
        Long aceptada = redis.eval(SCRIPT, ScriptOutputType.INTEGER, new String[] {"tasa:" + cliente},
                String.valueOf(VENTANA_MS), String.valueOf(LIMITE), UUID.randomUUID().toString());
        return aceptada == 1;
    }
}
