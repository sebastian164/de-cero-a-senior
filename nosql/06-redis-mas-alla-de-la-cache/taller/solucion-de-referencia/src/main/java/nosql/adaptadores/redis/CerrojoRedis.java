package nosql.adaptadores.redis;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.Optional;
import java.util.UUID;
import nosql.aplicacion.CerrojoDeTareas;
import nosql.aplicacion.RegistroDeTurnos;

/**
 * Cerrojo con ficha (solo el dueño lo libera) y turno. La exclusión la da Redis ({@code SET NX PX}); el turno lo emite
 * {@link RegistroDeTurnos} (MongoDB), después de tomar el cerrojo, y se confirma que el cerrojo sigue siendo nuestro cuando llega:
 * así un dueño que se quedó parado entre tomar el cerrojo y pedir el turno no se lleva un turno más nuevo que el del dueño legítimo.
 */
public final class CerrojoRedis implements CerrojoDeTareas {

    static final String LIBERAR = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
    public static final String PREFIJO = "pedidos:cerrojo:v1:";

    private final RedisCommands<String, String> redis;
    private final RegistroDeTurnos turnos;

    public CerrojoRedis(RedisCommands<String, String> redis, RegistroDeTurnos turnos) {
        this.redis = redis;
        this.turnos = turnos;
    }

    @Override
    public Optional<Concesion> adquirir(String tarea, long vidaMs) {
        String clave = PREFIJO + tarea, ficha = UUID.randomUUID().toString();
        if (!"OK".equals(redis.set(clave, ficha, SetArgs.Builder.nx().px(vidaMs)))) {
            return Optional.empty();
        }
        long turno;
        try {
            turno = turnos.siguiente(tarea);
        } catch (RuntimeException error) {
            soltar(clave, ficha);
            throw error;
        }
        if (!ficha.equals(redis.get(clave))) {
            return Optional.empty();                                  // el cerrojo caducó mientras se pedía el turno: no somos el dueño
        }
        return Optional.of(new Concesion(tarea, ficha, turno));
    }

    @Override
    public boolean liberar(Concesion concesion) {
        return soltar(PREFIJO + concesion.tarea(), concesion.ficha());
    }

    private boolean soltar(String clave, String ficha) {
        Long r = redis.eval(LIBERAR, ScriptOutputType.INTEGER, new String[]{clave}, ficha);
        return r != null && r == 1;
    }
}
