import io.lettuce.core.api.sync.RedisCommands;

/** Como mucho 5 peticiones por segundo por cliente: un contador que vive un segundo desde la primera petición. */
final class LimiteDeTasa {

    private static final int LIMITE = 5;

    private final RedisCommands<String, String> redis;
    private boolean caerseTrasContar = false;

    LimiteDeTasa(RedisCommands<String, String> redis) {
        this.redis = redis;
    }

    boolean permitir(String cliente) {
        String clave = "tasa:" + cliente;
        long peticiones = redis.incr(clave);
        if (caerseTrasContar) {
            caerseTrasContar = false;
            throw new IllegalStateException("el proceso se reinició justo después de contar");
        }
        if (peticiones == 1) {
            redis.pexpire(clave, 1_000);                         // la ventana empieza con la primera petición
        }
        return peticiones <= LIMITE;
    }

    /** Para la demostración: la próxima llamada falla entre las dos órdenes. */
    void caerseEnLaProxima() {
        caerseTrasContar = true;
    }
}
