package banco.jpa;

import banco.aplicacion.CuentaNoEncontrada;
import banco.aplicacion.VistaDeCuenta;
import banco.aplicacion.puertos.entrada.AbrirCuenta;
import banco.aplicacion.puertos.entrada.ConsultarCuenta;
import banco.aplicacion.puertos.entrada.Depositar;
import banco.aplicacion.puertos.entrada.TransferirDinero;
import banco.aplicacion.puertos.entrada.TransferirDinero.Orden;
import banco.aplicacion.puertos.entrada.TransferirDinero.Resultado;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Traduce HTTP y JSON a los puertos de entrada. Los mismos puertos que usaban la consola y el servidor del JDK. */
@RestController
public class ControladorDeCuentas {

    public record Apertura(String titular, long centavos) {
    }

    public record Deposito(long centavos) {
    }

    public record Transferencia(String origen, String destino, long centavos) {
    }

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
    public ResponseEntity<VistaDeCuenta> abrir(@RequestBody Apertura apertura) {
        return ResponseEntity.status(HttpStatus.CREATED).body(abrirCuenta.abrir(apertura.titular(), apertura.centavos()));
    }

    @PostMapping("/cuentas/{id}/depositos")
    public VistaDeCuenta depositar(@PathVariable String id, @RequestBody Deposito deposito) {
        return depositar.depositar(id, deposito.centavos());
    }

    @PostMapping("/transferencias")
    public ResponseEntity<Map<String, String>> transferir(@RequestHeader("Idempotency-Key") String clave,
                                                          @RequestBody Transferencia transferencia) {
        Resultado resultado = transferirDinero.transferir(
                new Orden(clave, transferencia.origen(), transferencia.destino(), transferencia.centavos()));
        HttpStatus estado = resultado == Resultado.REALIZADA ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(estado).body(Map.of("resultado", resultado.name()));
    }

    @GetMapping("/cuentas/{id}")
    public VistaDeCuenta consultar(@PathVariable String id) {
        return consultarCuenta.porId(id).orElseThrow(() -> new CuentaNoEncontrada(id));
    }
}
