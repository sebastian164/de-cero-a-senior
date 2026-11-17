import io.lettuce.core.SetArgs;
import io.lettuce.core.api.sync.RedisCommands;

/**
 * El borde HTTP de "pagar un servicio", con la clave de idempotencia reservada de forma atómica:
 *   SET pago:{clave} EN_CURSO NX EX 30  -> solo una petición la consigue; las demás saben que hay otra en curso
 *   al terminar, el resultado sustituye a la reserva y se guarda 24 horas, que es lo que el cliente puede tardar en reintentar
 * Se guarda también una huella de la petición: la misma clave con otro contenido es un error del cliente, no una repetición.
 */
final class Idempotencia {

    private static final long RESERVA_SEGUNDOS = 30;
    private static final long RESULTADO_SEGUNDOS = 24 * 60 * 60;

    private final RedisCommands<String, String> redis;
    private final Pasarela pasarela;

    Idempotencia(RedisCommands<String, String> redis, Pasarela pasarela) {
        this.redis = redis;
        this.pasarela = pasarela;
    }

    String pagar(String clave, String cuenta, long centavos) {
        String llave = "pago:" + clave;
        String huella = cuenta + ":" + centavos;
        String reservada = redis.set(llave, "EN_CURSO|" + huella, SetArgs.Builder.nx().ex(RESERVA_SEGUNDOS));
        if (!"OK".equals(reservada)) {
            return responderRepeticion(redis.get(llave), huella);
        }
        try {
            String cobro = pasarela.cobrar(cuenta, centavos);
            redis.set(llave, "HECHO|" + huella + "|" + cobro, SetArgs.Builder.ex(RESULTADO_SEGUNDOS));
            return "201 " + cobro;
        } catch (RuntimeException fallo) {
            redis.del(llave);                                    // no se cobró: que el cliente pueda reintentar
            throw fallo;
        }
    }

    private static String responderRepeticion(String guardado, String huella) {
        if (guardado == null) {
            return "409 en curso, reintenta";                    // la reserva caducó entre el SET y el GET
        }
        String[] partes = guardado.split("\\|", 3);
        if (!partes[1].equals(huella)) {
            return "422 la clave ya se usó con otra petición";
        }
        if (partes[0].equals("EN_CURSO")) {
            return "409 en curso, reintenta";
        }
        return "200 " + partes[2] + " (repetida)";
    }
}
