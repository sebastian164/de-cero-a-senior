import io.lettuce.core.api.sync.RedisCommands;

/** El borde HTTP de "pagar un servicio": la cabecera Idempotency-Key evita cobrar dos veces la misma petición. */
final class Idempotencia {

    private final RedisCommands<String, String> redis;
    private final Pasarela pasarela;

    Idempotencia(RedisCommands<String, String> redis, Pasarela pasarela) {
        this.redis = redis;
        this.pasarela = pasarela;
    }

    String pagar(String clave, String cuenta, long centavos) {
        String anterior = redis.get("pago:" + clave);            // ¿ya la atendimos?
        if (anterior != null) {
            return "200 " + anterior + " (repetida)";
        }
        String cobro = pasarela.cobrar(cuenta, centavos);        // entre el GET y el SET pasan 100 ms
        redis.set("pago:" + clave, cobro);                       // y se guarda para siempre
        return "201 " + cobro;
    }
}
