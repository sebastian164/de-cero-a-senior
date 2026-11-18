package banco.adaptadores.entrada.webflux;

import banco.adaptadores.entrada.webflux.Peticiones.Apertura;
import banco.adaptadores.entrada.webflux.Peticiones.Deposito;
import banco.adaptadores.entrada.webflux.Peticiones.Transferencia;
import banco.aplicacion.CuentaNoEncontrada;
import banco.aplicacion.VistaDeCuenta;
import banco.aplicacion.puertos.entrada.AbrirCuenta;
import banco.aplicacion.puertos.entrada.ConsultarCuenta;
import banco.aplicacion.puertos.entrada.Depositar;
import banco.aplicacion.puertos.entrada.TransferirDinero;
import banco.aplicacion.puertos.entrada.TransferirDinero.Orden;
import banco.aplicacion.puertos.entrada.TransferirDinero.Resultado;
import banco.dominio.Movimiento;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Un adaptador WebFlux sobre los puertos síncronos del núcleo. Compila, responde bien y pasa cualquier prueba
 * funcional. Pero cada llamada al núcleo es JDBC, y se ejecuta en el hilo del bucle de eventos.
 */
@RestController
public class ControladorDeCuentas {

    private final AbrirCuenta abrirCuenta;
    private final Depositar depositar;
    private final TransferirDinero transferirDinero;
    private final ConsultarCuenta consultarCuenta;

    public ControladorDeCuentas(AbrirCuenta abrirCuenta, Depositar depositar, TransferirDinero transferirDinero,
                                ConsultarCuenta consultarCuenta) {
        this.abrirCuenta = abrirCuenta;
        this.depositar = depositar;
        this.transferirDinero = transferirDinero;
        this.consultarCuenta = consultarCuenta;
    }

    @PostMapping("/cuentas")
    public Mono<ResponseEntity<VistaDeCuenta>> abrir(@RequestBody Apertura apertura) {
        VistaDeCuenta cuenta = abrirCuenta.abrir(apertura.titular(), apertura.centavos());
        return Mono.just(ResponseEntity.status(HttpStatus.CREATED).body(cuenta));
    }

    @PostMapping("/cuentas/{id}/depositos")
    public Mono<VistaDeCuenta> depositar(@PathVariable String id, @RequestBody Deposito deposito) {
        return Mono.just(depositar.depositar(id, deposito.centavos()));
    }

    @PostMapping("/transferencias")
    public Mono<ResponseEntity<Map<String, String>>> transferir(@RequestHeader("Idempotency-Key") String clave,
                                                                @RequestBody Transferencia transferencia) {
        Resultado resultado = transferirDinero.transferir(
                new Orden(clave, transferencia.origen(), transferencia.destino(), transferencia.centavos()));
        HttpStatus estado = resultado == Resultado.REALIZADA ? HttpStatus.CREATED : HttpStatus.OK;
        return Mono.just(ResponseEntity.status(estado).body(Map.of("resultado", resultado.name())));
    }

    @GetMapping("/cuentas/{id}")
    public Mono<VistaDeCuenta> consultar(@PathVariable String id) {
        return Mono.justOrEmpty(consultarCuenta.porId(id))
                .switchIfEmpty(Mono.error(new CuentaNoEncontrada(id)));
    }

    @GetMapping("/cuentas/{id}/movimientos")
    public Flux<Movimiento> movimientos(@PathVariable String id) {
        return Flux.fromIterable(consultarCuenta.movimientosDe(id));
    }
}
