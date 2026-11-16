package servicio;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** El servicio con tres dependencias: una crítica (catálogo) y dos opcionales (recomendaciones y reseñas). */
@SpringBootApplication
public class AplicacionServicio {

    static RestClient cliente(String url, Duration conexion, Duration respuesta) {
        var f = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(conexion).build());
        f.setReadTimeout(respuesta);
        return RestClient.builder().baseUrl(url).requestFactory(f).build();
    }

    @Bean RestClient clienteCritico(@Value("${dep.critico}") String url) { return cliente(url, Duration.ofMillis(300), Duration.ofMillis(800)); }

    @Bean RestClient recomendacionesRapido(@Value("${dep.recomendaciones}") String url) { return cliente(url, Duration.ofMillis(300), Duration.ofMillis(400)); }

    @Bean RestClient clienteResenas(@Value("${dep.resenas}") String url) { return cliente(url, Duration.ofMillis(300), Duration.ofMillis(400)); }

    /** Cliente "descuidado": espera hasta 6 s, como un valor por defecto generoso. */
    @Bean RestClient recomendacionesLento(@Value("${dep.recomendaciones}") String url) { return cliente(url, Duration.ofSeconds(2), Duration.ofSeconds(6)); }
}
