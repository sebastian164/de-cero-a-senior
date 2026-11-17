import io.lettuce.core.SetArgs;
import io.lettuce.core.api.sync.RedisCommands;

/** Un cerrojo entre instancias: quien consigue crear la clave, trabaja; al terminar, la borra. Caduca por si alguien muere. */
final class Cerrojo {

    private final RedisCommands<String, String> redis;

    Cerrojo(RedisCommands<String, String> redis) {
        this.redis = redis;
    }

    boolean tomar(String recurso, long vidaMs) {
        return "OK".equals(redis.set("cerrojo:" + recurso, "ocupado", SetArgs.Builder.nx().px(vidaMs)));
    }

    void soltar(String recurso) {
        redis.del("cerrojo:" + recurso);
    }
}
