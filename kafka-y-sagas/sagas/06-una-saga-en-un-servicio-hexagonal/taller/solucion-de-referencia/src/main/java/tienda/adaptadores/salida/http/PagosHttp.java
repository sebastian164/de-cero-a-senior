package tienda.adaptadores.salida.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import tienda.aplicacion.saga.Puertos.Pagos;
import tienda.aplicacion.saga.Puertos.SinRespuesta;

/**
 * Paso 10: adaptador real del puerto Pagos sobre HTTP. Es el único archivo nuevo: el orquestador, el dominio y los
 * otros puertos no cambian. La clave de idempotencia es el id de la saga. Un tiempo agotado o un 5xx es
 * "no se sabe" (SinRespuesta); un 402 es un rechazo definitivo del negocio.
 */
public final class PagosHttp implements Pagos {

    private final HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
    private final URI base;

    public PagosHttp(URI base) {
        this.base = base;
    }

    @Override
    public Respuesta cobrar(String sagaId, long centavos) {
        int estado = enviar("/cobros/" + sagaId + "?centavos=" + centavos);
        return switch (estado) {
            case 200 -> Respuesta.APROBADO;
            case 402 -> Respuesta.RECHAZADO;
            default -> throw new SinRespuesta("pagos respondió " + estado);
        };
    }

    @Override
    public void reembolsar(String sagaId) {
        int estado = enviar("/reembolsos/" + sagaId);
        if (estado != 200) {
            throw new SinRespuesta("pagos respondió " + estado);
        }
    }

    private int enviar(String ruta) {
        HttpRequest peticion = HttpRequest.newBuilder(base.resolve(ruta)).timeout(Duration.ofSeconds(2))
                .header("Idempotency-Key", ruta.substring(ruta.lastIndexOf('/') + 1).split("\\?")[0])
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        try {
            return cliente.send(peticion, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException e) {
            throw new SinRespuesta("pagos sin respuesta: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SinRespuesta("interrumpido");
        }
    }
}
