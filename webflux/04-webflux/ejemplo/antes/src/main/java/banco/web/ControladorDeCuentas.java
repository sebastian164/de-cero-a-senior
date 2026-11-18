package banco.web;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Mono;

/** Un controlador anotado que devuelve Mono en todas partes. Parece reactivo; por dentro, no lo es. */
@RestController
public class ControladorDeCuentas {

    private final AlmacenDeCuentas almacen;
    private final RestClient riesgo;

    public ControladorDeCuentas(AlmacenDeCuentas almacen, @Value("${riesgo.url}") String urlDeRiesgo) {
        this.almacen = almacen;
        this.riesgo = RestClient.builder()                       // el cliente HTTP "de siempre": bloqueante
                .requestFactory(new JdkClientHttpRequestFactory())
                .baseUrl(urlDeRiesgo)
                .build();
    }

    @GetMapping("/cuentas/{id}")
    public Mono<Cuenta> consultar(@PathVariable String id) {
        return almacen.buscar(id)
                .switchIfEmpty(Mono.error(new RuntimeException("no existe la cuenta " + id)));
    }

    @GetMapping("/cuentas/{id}/riesgo")
    public Mono<Riesgo> riesgo(@PathVariable String id) {
        Riesgo respuesta = riesgo.get().uri("/riesgo/{id}", id)  // el hilo del bucle espera aquí 300 ms
                .retrieve()
                .body(Riesgo.class);
        return Mono.just(respuesta);
    }

    @GetMapping("/cuentas/{id}/extracto")
    public Mono<List<Movimiento>> extracto(@PathVariable String id) {
        return almacen.extracto(id).collectList();              // nadie ve nada hasta tener el último renglón
    }
}
