package banco.transferencias.adaptador.entrada.web;

import banco.transferencias.aplicacion.puerto.entrada.TransferirDinero;
import banco.transferencias.dominio.Transferencia;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Adaptador primario REST: solo traduce. */
@RestController
class TransferenciaController {
    record Peticion(@NotBlank String origen, @NotBlank String destino, @Positive long centavos) { }
    record Respuesta(String id, boolean aceptada, String motivo) { }

    private final TransferirDinero transferir;

    TransferenciaController(TransferirDinero transferir) { this.transferir = transferir; }

    @PostMapping("/transferencias")
    ResponseEntity<Respuesta> crear(@Valid @RequestBody Peticion p) {
        Transferencia t;
        try {
            t = transferir.transferir(new TransferirDinero.Comando(p.origen(), p.destino(), p.centavos()));
        } catch (IllegalArgumentException e) {      // formato inválido (por ejemplo, número de cuenta): error del cliente, no 500
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        var cuerpo = new Respuesta(t.id().toString(), t.aceptada(), t.motivoRechazo());
        return ResponseEntity.status(t.aceptada() ? HttpStatus.CREATED : HttpStatus.UNPROCESSABLE_CONTENT).body(cuerpo);
    }
}
