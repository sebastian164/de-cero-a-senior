package acortador;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ControladorUrls {
    public record Solicitud(String url, String alias, Instant expiraEn) { }

    private final ServicioUrls servicio;
    private final ContadorDeClics clics;
    private final Propiedades propiedades;

    public ControladorUrls(ServicioUrls servicio, ContadorDeClics clics, Propiedades propiedades) {
        this.servicio = servicio;
        this.clics = clics;
        this.propiedades = propiedades;
    }

    @PostMapping("/v1/urls")
    public ResponseEntity<Map<String, String>> crear(@RequestBody Solicitud s) {
        String codigo = servicio.crear(s.url(), s.alias(), s.expiraEn());
        return ResponseEntity.created(URI.create(propiedades.dominio() + codigo))
                .body(Map.of("corta", propiedades.dominio() + codigo, "codigo", codigo));
    }

    /** 302: cada visita pasa por el servicio, así que se pueden contar los clics (con 301 el navegador no volvería). */
    @GetMapping("/{codigo:[A-Za-z0-9_-]{1,16}}")
    public ResponseEntity<Void> redirigir(@PathVariable String codigo) {
        return servicio.resolver(codigo)
                .map(destino -> {
                    clics.registrar(codigo);
                    return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(destino)).<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/v1/urls/{codigo}/estadisticas")
    public ResponseEntity<Map<String, Object>> estadisticas(@PathVariable String codigo) {
        return servicio.clics(codigo).map(c -> ResponseEntity.ok(Map.<String, Object>of("codigo", codigo, "clics", c)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @ExceptionHandler(ServicioUrls.AliasOcupado.class)
    ResponseEntity<Map<String, String>> conflicto() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "alias ocupado"));
    }

    @ExceptionHandler(ServicioUrls.SolicitudInvalida.class)
    ResponseEntity<Map<String, String>> invalida(ServicioUrls.SolicitudInvalida e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
