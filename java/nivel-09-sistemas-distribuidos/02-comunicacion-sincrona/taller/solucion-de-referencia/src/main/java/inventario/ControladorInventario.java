package inventario;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ControladorInventario {

    public record Existencias(long productoId, int cantidad) {}
    public record CobroPedido(long pedidoId, BigDecimal monto) {}
    public record ResultadoCobro(long cobroId, long pedidoId, String estado) {}

    private final Comportamiento comportamiento;
    private final Cobros cobros;

    ControladorInventario(Comportamiento comportamiento, Cobros cobros) {
        this.comportamiento = comportamiento;
        this.cobros = cobros;
    }

    @GetMapping("/existencias/{id}")
    ResponseEntity<Existencias> existencias(@PathVariable long id) throws InterruptedException {
        comportamiento.alLlegar();
        if (id == 404) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new Existencias(id, 7));
    }

    /** No idempotente: cada llamada cobra. */
    @PostMapping("/cobros")
    ResultadoCobro cobrar(@RequestBody CobroPedido cobro) throws InterruptedException {
        ResultadoCobro r = cobros.cobrar(cobro);
        comportamiento.antesDeResponderCobro();
        return r;
    }

    /** Idempotente: la misma clave devuelve el mismo resultado sin cobrar de nuevo. */
    @PostMapping("/cobros-idempotentes")
    ResultadoCobro cobrarIdempotente(@RequestHeader("Idempotency-Key") String clave, @RequestBody CobroPedido cobro)
            throws InterruptedException {
        ResultadoCobro r = cobros.cobrarUnaVez(clave, cobro);
        comportamiento.antesDeResponderCobro();
        return r;
    }

    @GetMapping("/cobros/cuenta/{pedidoId}")
    Map<String, Integer> cuenta(@PathVariable long pedidoId) {
        return Map.of("cobros", cobros.contar(pedidoId));
    }
}
