package citas;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;

@RestController
@RequestMapping("/v1/citas")
class CitasV1Controller {

    private final ServicioCitas servicio;

    CitasV1Controller(ServicioCitas servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    ResponseEntity<CitaV1> crear(@RequestHeader("Idempotency-Key") String clave,
                                 @Valid @RequestBody CrearCitaRequest request) {
        ServicioCitas.Creada creada = servicio.crear(clave, request);
        return ResponseEntity.created(URI.create("/v1/citas/" + creada.cita().getId()))
                .header("Idempotent-Replayed", String.valueOf(creada.repetida()))
                .body(CitaV1.de(creada.cita()));
    }

    @GetMapping("/{id}")
    CitaV1 buscar(@PathVariable Long id) {
        return CitaV1.de(servicio.buscar(id));
    }

    @GetMapping
    Page<CitaV1> listar(@RequestParam(required = false) Long medicoId,
                        @RequestParam(required = false) EstadoCita estado,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                        @PageableDefault(size = 20, sort = "inicio") Pageable pagina) {
        return servicio.listar(medicoId, estado, desde, pagina).map(CitaV1::de);
    }

    @PostMapping("/{id}/cancelacion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancelar(@PathVariable Long id) {
        servicio.cancelar(id);
    }
}
