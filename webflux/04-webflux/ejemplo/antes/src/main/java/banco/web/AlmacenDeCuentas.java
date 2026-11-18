package banco.web;

import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Cuentas en memoria. La base de datos reactiva llega en la sesión 05; aquí el tema es la capa web. */
@Component
public class AlmacenDeCuentas {

    private final Map<String, Cuenta> cuentas = Map.of(
            "cuenta-1", new Cuenta("cuenta-1", "Ana", 100_000_000),
            "cuenta-2", new Cuenta("cuenta-2", "Luis", 20_000_000));

    public Mono<Cuenta> buscar(String id) {
        return Mono.justOrEmpty(cuentas.get(id));
    }

    /** Un extracto largo que llega renglón a renglón, uno cada 100 ms, como lo entregaría una consulta grande. */
    public Flux<Movimiento> extracto(String cuentaId) {
        return Flux.interval(Duration.ofMillis(100))
                .take(10)
                .map(i -> new Movimiento(cuentaId, i.intValue() + 1, 1_000_00L * (i + 1), "movimiento " + (i + 1)));
    }
}
