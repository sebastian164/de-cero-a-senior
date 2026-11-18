package banco.cuentas;

import banco.cuentas.Peticiones.Apertura;
import banco.cuentas.Peticiones.Transferencia;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** Firmas reactivas, cuerpos bloqueantes: Mono.just(...) evalúa la llamada JDBC en el hilo del bucle. */
@RestController
public class ControladorDeCuentas {

    private final AlmacenJdbc almacen;

    public ControladorDeCuentas(AlmacenJdbc almacen) {
        this.almacen = almacen;
    }

    @PostMapping("/cuentas")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Cuenta> abrir(@RequestBody Apertura apertura) {
        return Mono.just(almacen.abrir(apertura.titular(), apertura.centavos()));
    }

    @GetMapping("/cuentas/{id}")
    public Mono<Cuenta> consultar(@PathVariable String id) {
        return Mono.just(almacen.buscar(id));
    }

    @GetMapping("/cuentas/{id}/resumen")
    public Mono<Resumen> resumen(@PathVariable String id) {
        return Mono.just(almacen.resumen(id));
    }

    @PostMapping("/transferencias")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Void> transferir(@RequestBody Transferencia transferencia) {
        almacen.transferir(transferencia.origen(), transferencia.destino(), transferencia.centavos());
        return Mono.empty();
    }
}
