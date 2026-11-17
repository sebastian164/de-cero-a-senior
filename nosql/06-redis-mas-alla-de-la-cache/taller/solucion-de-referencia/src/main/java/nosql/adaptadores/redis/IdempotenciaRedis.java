package nosql.adaptadores.redis;

import io.lettuce.core.RedisException;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.concurrent.atomic.AtomicLong;
import nosql.aplicacion.RegistroDeIdempotencia;

/**
 * Idempotencia del borde. Clave pedidos:idem:v1:{clave}; EN_CURSO caduca pronto (si el proceso muere, se puede reintentar),
 * HECHO guarda la respuesta 24 h. Si Redis falla se degrada: la unicidad que de verdad protege está en MongoDB.
 */
public final class IdempotenciaRedis implements RegistroDeIdempotencia {

    public static final String PREFIJO = "pedidos:idem:v1:";
    public final AtomicLong degradadas = new AtomicLong();
    private final RedisCommands<String, String> redis;
    private final long enCursoMs;

    public IdempotenciaRedis(RedisCommands<String, String> redis, long enCursoMs) { this.redis = redis; this.enCursoMs = enCursoMs; }

    @Override
    public Resultado iniciar(String clave, String huella) {
        try {
            for (int i = 0; i < 3; i++) {
                if ("OK".equals(redis.set(PREFIJO + clave, "EN_CURSO|" + huella, SetArgs.Builder.nx().px(enCursoMs)))) return new Resultado(Tipo.NUEVA, 0, null);
                String actual = redis.get(PREFIJO + clave);
                if (actual == null) continue;
                String[] p = actual.split("\\|", 4);
                if (!p[1].equals(huella)) return new Resultado(Tipo.CUERPO_DISTINTO, 0, null);
                if (p[0].equals("EN_CURSO")) return new Resultado(Tipo.EN_CURSO, 0, null);
                return new Resultado(Tipo.REPETIDA, Integer.parseInt(p[2]), p[3]);
            }
            return new Resultado(Tipo.EN_CURSO, 0, null);
        } catch (RedisException e) {
            degradadas.incrementAndGet();
            return new Resultado(Tipo.NUEVA, 0, null);
        }
    }

    @Override
    public void completar(String clave, String huella, int status, String cuerpo) {
        try { redis.set(PREFIJO + clave, "HECHO|" + huella + "|" + status + "|" + cuerpo, SetArgs.Builder.ex(24 * 3600)); }
        catch (RedisException e) { degradadas.incrementAndGet(); }
    }

    @Override
    public void abandonar(String clave) {
        try { redis.del(PREFIJO + clave); } catch (RedisException e) { degradadas.incrementAndGet(); }
    }
}
