package nosql.adaptadores.redis;

import io.lettuce.core.RedisException;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import nosql.aplicacion.LimitadorDeTasa;

/**
 * Ventana deslizante con registro (ZSET) en un script de Lua: nunca deja pasar más de {@code limite} en cualquier ventana,
 * ni en el borde, cosa que una ventana fija no garantiza. Usa el reloj de Redis, no el de las instancias.
 */
public final class LimiteDeTasaRedis implements LimitadorDeTasa {
    public enum SiRedisFalla { ABIERTO, CERRADO }

    public static final String PREFIJO = "pedidos:limite:v1:";
    static final String LUA = """
        local t = redis.call('TIME')
        local ahora = t[1] * 1000 + math.floor(t[2] / 1000)
        redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ahora - tonumber(ARGV[2]))
        if redis.call('ZCARD', KEYS[1]) < tonumber(ARGV[1]) then
          redis.call('ZADD', KEYS[1], ahora, ARGV[3])
          redis.call('PEXPIRE', KEYS[1], ARGV[2])
          return {1, 0}
        end
        local mas_viejo = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
        return {0, tonumber(mas_viejo[2]) + tonumber(ARGV[2]) - ahora}
        """;

    public final AtomicLong rechazadas = new AtomicLong(), redisCaido = new AtomicLong();
    private final RedisCommands<String, String> redis;
    private final int limite;
    private final long ventanaMs;
    private final SiRedisFalla politica;

    public LimiteDeTasaRedis(RedisCommands<String, String> redis, int limite, long ventanaMs, SiRedisFalla politica) {
        this.redis = redis; this.limite = limite; this.ventanaMs = ventanaMs; this.politica = politica;
    }

    @Override
    public Decision intentar(String cliente) {
        try {
            List<Object> r = redis.eval(LUA, ScriptOutputType.MULTI, new String[]{PREFIJO + cliente}, "" + limite, "" + ventanaMs, UUID.randomUUID().toString());
            boolean ok = ((Long) r.get(0)) == 1;
            if (!ok) rechazadas.incrementAndGet();
            return new Decision(ok, (Long) r.get(1), false);
        } catch (RedisException e) {
            redisCaido.incrementAndGet();
            return new Decision(politica == SiRedisFalla.ABIERTO, 1000, true);
        }
    }
}
