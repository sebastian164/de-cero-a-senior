package tienda.adaptadores.salida.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import tienda.aplicacion.EventoDePedido;
import tienda.aplicacion.puertos.salida.PublicadorDeEventos;

/**
 * Paso 6, el defecto: publicar es llamar por HTTP al servicio de notificaciones. El caso de uso publica DENTRO de su
 * unidad de trabajo, así que la conexión queda ocupada lo que tarde ese servicio. Lo usa también el mensajero del arreglo.
 * Lanza una excepción si el servicio no responde o no contesta 200.
 */
public final class NotificadorHttp implements PublicadorDeEventos {

    private final HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final String url;

    public NotificadorHttp(String url) {
        this.url = url;
    }

    @Override
    public void publicar(EventoDePedido evento) {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(url + "/notificar?evento=" + evento.id().replace(':', '-')))
                .timeout(Duration.ofSeconds(30)).build();
        try {
            HttpResponse<Void> respuesta = cliente.send(peticion, HttpResponse.BodyHandlers.discarding());
            if (respuesta.statusCode() != 200) {
                throw new IllegalStateException("notificaciones respondió " + respuesta.statusCode());
            }
        } catch (IOException error) {
            throw new IllegalStateException("notificaciones no responde", error);
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupcion);
        }
    }
}
