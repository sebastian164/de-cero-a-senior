package nosql;

import comun.Salida;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import nosql.adaptadores.redis.ClienteRedis;
import nosql.aplicacion.PoliticaDeReintento;

/**
 * Un proceso que atiende una petición "crear pedido con cobro" por HTTP y se detiene después de cobrar y antes de guardar la respuesta
 * (paso 2). La prueba lo mata con kill -9 en cuanto imprime COBRADO.
 */
public final class ProcesoDeCobro {
    public static void main(String[] a) throws Exception {
        String mongo = a[0], bd = a[1], redisUrl = a[2], modo = a[3], clave = a[4];
        long pausaMs = Long.parseLong(a[5]), enCursoMs = Long.parseLong(a[6]);
        var sistema = Composicion.elegir("mongo", mongo, bd, PoliticaDeReintento.inmediata(3), Clock.systemUTC());
        var redis = ClienteRedis.conTiemposCortos(redisUrl, Duration.ofMillis(500)).connect().sync();
        var config = nosql.proteccion.Composicion.Config.por(1000, 60_000).conEnCurso(enCursoMs).conPasarela(modo.equals("idempotente")).alCobrar(() -> {
            System.out.println("COBRADO");
            System.out.flush();
            try { Thread.sleep(pausaMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        var grafo = nosql.proteccion.Composicion.montar(sistema, sistema.productos(), redis, 0, config, null);
        var r = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + grafo.http().puerto() + "/pedidos"))
                .header("Idempotency-Key", clave).POST(HttpRequest.BodyPublishers.ofString(ProteccionTest.CUERPO)).build(), HttpResponse.BodyHandlers.ofString());
        System.out.println("RESPUESTA " + r.statusCode());
        System.exit(0);
    }
}
