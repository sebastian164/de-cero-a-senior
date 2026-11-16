package servicio;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
public class Controlador {

    static class CriticoNoDisponible extends RuntimeException {
        CriticoNoDisponible(Throwable causa) { super("catálogo no disponible", causa); }
    }

    private final RestClient critico;
    private final Recomendaciones recomendaciones;
    private final RecomendacionesSinProteccion sinProteccion;
    private final Resenas resenas;

    Controlador(@Qualifier("clienteCritico") RestClient critico, Recomendaciones recomendaciones,
                RecomendacionesSinProteccion sinProteccion, Resenas resenas) {
        this.critico = critico;
        this.recomendaciones = recomendaciones;
        this.sinProteccion = sinProteccion;
        this.resenas = resenas;
    }

    private Map<String, Object> producto(long id) {
        try {
            return critico.get().uri("/datos/{id}", id).retrieve().body(Map.class);
        } catch (RuntimeException e) {
            throw new CriticoNoDisponible(e);                    // crítica: sin ella no se puede atender
        }
    }

    @GetMapping("/pagina/{id}")
    Map<String, Object> pagina(@PathVariable long id) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("producto", producto(id));
        r.put("recomendaciones", recomendaciones.recomendar(id));
        r.put("resenas", resenas.listar(id));
        return r;
    }

    /** Antes: la misma página, con la dependencia opcional sin mamparo y con un tiempo límite de 6 s. */
    @GetMapping("/pagina-sin-mamparo/{id}")
    Map<String, Object> paginaSinMamparo(@PathVariable long id) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("producto", producto(id));
        r.put("recomendaciones", sinProteccion.recomendar(id));
        return r;
    }

    /** Una ruta que no usa ninguna dependencia opcional. */
    @GetMapping("/precio/{id}")
    Map<String, Object> precio(@PathVariable long id) {
        return Map.of("id", id, "precio", 100, "producto", producto(id));
    }

    /** Ruta real protegida por el límite de tasa (FiltroLimiteDeTasa). */
    @GetMapping("/limitado/ping")
    String limitadoPing() {
        return "pong";
    }

    @GetMapping("/ping")
    String ping() {
        return "pong";
    }

    // ----- respaldo adecuado frente a respaldo engañoso -----

    /** ENGAÑOSO: si el libro mayor falla devuelve saldo 0. El usuario cree que no tiene dinero. */
    @GetMapping("/saldo-enganoso/{id}")
    Map<String, Object> saldoEnganoso(@PathVariable long id) {
        try {
            critico.get().uri("/datos/{id}", id).retrieve().body(Map.class);
            return Map.of("saldo", 250_000);
        } catch (RuntimeException e) {
            return Map.of("saldo", 0);
        }
    }

    /** ADECUADO: un saldo no se puede inventar; se dice que no está disponible (503) y el cliente decide. */
    @GetMapping("/saldo/{id}")
    Map<String, Object> saldo(@PathVariable long id) {
        return Map.of("saldo", 250_000, "consultado", producto(id).get("valor"));
    }

    @ExceptionHandler(CriticoNoDisponible.class)
    ResponseEntity<Map<String, String>> criticoCaido(CriticoNoDisponible e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "SERVICIO_NO_DISPONIBLE", "detalle", e.getMessage()));
    }
}
