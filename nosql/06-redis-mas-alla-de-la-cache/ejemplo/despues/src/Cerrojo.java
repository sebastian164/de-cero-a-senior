import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.Optional;
import java.util.UUID;

/**
 * Un cerrojo entre instancias, con sus dos correcciones:
 *   cada dueño tiene una ficha propia, y solo quien la tiene puede soltarlo (comparar y borrar, en un script);
 *   cada vez que alguien lo toma recibe un turno mayor que el anterior, para que el recurso rechace a un dueño caducado.
 * Lo que NO resuelve: si el dueño se congela más de lo que dura el cerrojo, deja de ser el único. Por eso el turno.
 */
final class Cerrojo {

    /** Lo que recibe quien consigue el cerrojo. */
    record Permiso(String recurso, String ficha, long turno) {
    }

    private static final String TOMAR = """
            if redis.call('SET', KEYS[1], ARGV[1], 'NX', 'PX', ARGV[2]) then
              return redis.call('INCR', KEYS[2])
            end
            return 0
            """;

    private static final String SOLTAR = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """;

    private final RedisCommands<String, String> redis;

    Cerrojo(RedisCommands<String, String> redis) {
        this.redis = redis;
    }

    Optional<Permiso> tomar(String recurso, long vidaMs) {
        String ficha = UUID.randomUUID().toString();
        Long turno = redis.eval(TOMAR, ScriptOutputType.INTEGER, new String[] {"cerrojo:" + recurso, "cerrojo:" + recurso + ":turno"},
                ficha, String.valueOf(vidaMs));
        return turno == 0 ? Optional.empty() : Optional.of(new Permiso(recurso, ficha, turno));
    }

    /** Devuelve falso si el cerrojo ya no era de quien intenta soltarlo. */
    boolean soltar(Permiso permiso) {
        Long borradas = redis.eval(SOLTAR, ScriptOutputType.INTEGER, new String[] {"cerrojo:" + permiso.recurso()}, permiso.ficha());
        return borradas == 1;
    }
}
