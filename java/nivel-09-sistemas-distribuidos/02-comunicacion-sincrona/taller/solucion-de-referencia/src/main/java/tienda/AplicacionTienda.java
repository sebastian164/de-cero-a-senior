package tienda;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** El primer servicio: llama a inventario. */
@SpringBootApplication
public class AplicacionTienda {

    /** El ayudante que el README usa sin definir: tiempo límite de conexión y de respuesta. */
    static ClientHttpRequestFactory conTiemposLimite(Duration conexion, Duration respuesta) {
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(conexion).build());
        f.setReadTimeout(respuesta);
        return f;
    }

    /** Sin tiempo límite de respuesta: el valor por defecto del cliente HTTP del JDK es esperar para siempre. */
    @Bean
    RestClient clienteSinLimite(@Value("${inventario.url}") String url) {
        return RestClient.builder().baseUrl(url).requestFactory(new JdkClientHttpRequestFactory()).build();
    }

    @Bean
    RestClient clienteConLimite(@Value("${inventario.url}") String url) {
        return RestClient.builder().baseUrl(url)
                .requestFactory(conTiemposLimite(Duration.ofMillis(500), Duration.ofMillis(300)))
                .build();
    }
}
