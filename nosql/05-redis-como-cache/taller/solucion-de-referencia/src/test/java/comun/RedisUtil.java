package comun;

import io.lettuce.core.FlushMode;
import io.lettuce.core.api.sync.RedisCommands;

/** Utilidades de las pruebas con Redis. */
public final class RedisUtil {
    private RedisUtil() {}

    public static long usedMemory(RedisCommands<String, String> redis) {
        return Long.parseLong(redis.info("memory").lines().filter(l -> l.startsWith("used_memory:")).findFirst().orElseThrow().split(":")[1].trim());
    }

    /**
     * Vacía Redis de forma síncrona y espera a que la memoria se libere de verdad. Con el vaciado en segundo plano (lazyfree), la memoria de lo
     * borrado sigue contada un rato: si justo después se baja maxmemory, Redis ve que se pasa, no tiene claves que desalojar y responde OOM.
     */
    public static void vaciar(RedisCommands<String, String> redis) {
        redis.flushall(FlushMode.SYNC);
        for (int i = 0; i < 100 && usedMemory(redis) > 3_500_000; i++) Hilos.dormir(100);
    }

    /** Lo que hay que mirar cuando Redis responde OOM con allkeys-lru: la memoria y las estadísticas de desalojo en ese instante. */
    public static String diagnostico(RedisCommands<String, String> redis) {
        return "INFO memory:\n" + redis.info("memory") + "\nINFO stats:\n" + redis.info("stats") + "\nmaxmemory-policy=" + redis.configGet("maxmemory-policy")
                + " lazyfree=" + redis.configGet("lazyfree-lazy-eviction") + " dbsize=" + redis.dbsize();
    }
}
