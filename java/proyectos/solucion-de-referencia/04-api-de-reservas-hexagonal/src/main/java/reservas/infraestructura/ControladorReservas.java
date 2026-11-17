package reservas.infraestructura;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reservas.aplicacion.Puertos.Pagina;
import reservas.aplicacion.ServicioReservas;
import reservas.dominio.Franja;
import reservas.dominio.Reserva;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/reservas")
public class ControladorReservas {
    public record PeticionReserva(@NotBlank String espacio, @NotNull Instant inicio, @NotNull Instant fin) {}
    public record RespuestaReserva(UUID id, String espacio, String usuario, Instant inicio, Instant fin, String estado) {
        static RespuestaReserva de(Reserva r) {
            return new RespuestaReserva(r.id(), r.espacio(), r.usuario(), r.franja().inicio(), r.franja().fin(), r.estado().name());
        }
    }

    private final ServicioReservas servicio;

    public ControladorReservas(ServicioReservas servicio) { this.servicio = servicio; }

    @Operation(summary = "Crea una reserva; con Idempotency-Key, repetir la petición devuelve la misma reserva")
    @PostMapping
    public ResponseEntity<RespuestaReserva> reservar(@AuthenticationPrincipal Jwt jwt,
                                                     @RequestHeader(value = "Idempotency-Key", required = false) String clave,
                                                     @Valid @RequestBody PeticionReserva p) {
        Reserva r = servicio.reservar(Seguridad.actor(jwt), clave, p.espacio(), new Franja(p.inicio(), p.fin()));
        return ResponseEntity.status(HttpStatus.CREATED).body(RespuestaReserva.de(r));
    }

    @Operation(summary = "Lista paginada: cada usuario ve las suyas; el administrador, todas")
    @GetMapping
    public Pagina<RespuestaReserva> listar(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "10") int tamano) {
        if (pagina < 0 || tamano < 1 || tamano > 100) throw new IllegalArgumentException("pagina >= 0 y 1 <= tamano <= 100");
        Pagina<Reserva> p = servicio.listar(Seguridad.actor(jwt), pagina, tamano);
        return new Pagina<>(p.contenido().stream().map(RespuestaReserva::de).toList(), p.pagina(), p.tamano(), p.total());
    }

    public record RespuestaDisponibilidad(String espacio, java.util.List<Franja> ocupadas, java.util.List<Franja.Hueco> libres) {}

    @Operation(summary = "Disponibilidad por franjas de un espacio en una ventana: lo ocupado y los huecos libres")
    @GetMapping("/disponibilidad")
    public RespuestaDisponibilidad disponibilidad(@RequestParam String espacio, @RequestParam Instant desde, @RequestParam Instant hasta) {
        var d = servicio.disponibilidad(espacio, desde, hasta);
        return new RespuestaDisponibilidad(d.espacio(), d.ocupadas(), d.libres());
    }

    @GetMapping("/{id}")
    public RespuestaReserva ver(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return RespuestaReserva.de(servicio.ver(Seguridad.actor(jwt), id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        servicio.cancelar(Seguridad.actor(jwt), id);
    }

    @PostMapping("/espera")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void esperar(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PeticionReserva p) {
        servicio.esperar(Seguridad.actor(jwt), p.espacio(), new Franja(p.inicio(), p.fin()));
    }
}
