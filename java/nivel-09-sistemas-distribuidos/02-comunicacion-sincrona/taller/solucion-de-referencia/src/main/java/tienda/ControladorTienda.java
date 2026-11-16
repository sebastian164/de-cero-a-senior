package tienda;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

@RestController
public class ControladorTienda {

    private final ClienteInventario inventario;

    ControladorTienda(ClienteInventario inventario) {
        this.inventario = inventario;
    }

    /** Ruta que no depende de inventario. */
    @GetMapping("/salud")
    String salud() {
        return "ok";
    }

    @GetMapping("/productos-sin-limite/{id}")
    ClienteInventario.Existencias sinLimite(@PathVariable long id) {
        return inventario.consultarSinLimite(id);
    }

    @GetMapping("/productos-sin-reintento/{id}")
    ClienteInventario.Existencias sinReintento(@PathVariable long id) {
        return inventario.consultarUnaVez(id);
    }

    @GetMapping("/productos/{id}")
    ClienteInventario.Existencias producto(@PathVariable long id) {
        return inventario.consultar(id);
    }

    @PostMapping("/pagos/{pedidoId}")
    ClienteInventario.ResultadoCobro pagar(@PathVariable long pedidoId) {
        return inventario.cobrar(pedidoId, new BigDecimal("100.00"));
    }

    @PostMapping("/pagos-idempotentes/{pedidoId}")
    ClienteInventario.ResultadoCobro pagarIdempotente(@PathVariable long pedidoId) {
        return inventario.cobrarIdempotente(UUID.randomUUID().toString(), pedidoId, new BigDecimal("100.00"));
    }

    @ExceptionHandler(HttpClientErrorException.class)
    ResponseEntity<String> errorDelCliente(HttpClientErrorException e) {
        return ResponseEntity.status(e.getStatusCode()).body("inventario respondió " + e.getStatusCode().value());
    }

    @ExceptionHandler(RestClientException.class)
    ResponseEntity<String> dependenciaCaida(RestClientException e) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body("inventario no respondió: " + e.getClass().getSimpleName());
    }
}
