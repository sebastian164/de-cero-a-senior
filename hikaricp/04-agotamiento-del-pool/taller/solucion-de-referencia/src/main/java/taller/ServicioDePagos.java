package taller;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** El servicio externo simulado (herramientas/Externo.java): tarda lo que se le indique. */
@Component
public class ServicioDePagos {

    private final HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final String url;

    public ServicioDePagos(@Value("${externo.url}") String url) {
        this.url = url;
    }

    public void cobrar(long pedido) {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(url + "/pagar?pedido=" + pedido))
                .timeout(Duration.ofSeconds(30)).build();
        try {
            HttpResponse<String> respuesta = cliente.send(peticion, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() != 200) {
                throw new IllegalStateException("pagos respondió " + respuesta.statusCode());
            }
        } catch (IOException error) {
            throw new IllegalStateException("pagos no responde", error);
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupcion);
        }
    }
}
