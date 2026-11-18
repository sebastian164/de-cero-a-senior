package pedidos;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ControladorCorreos {
    private final ServicioCorreos correos;

    public ControladorCorreos(ServicioCorreos correos) {
        this.correos = correos;
    }

    @PostMapping("/api/correos/sincrono")
    public Map<String, Object> sincrono(@RequestParam(defaultValue = "500") long demoraMs) {
        correos.enviar(demoraMs);
        return Map.of("estado", "ENVIADO");
    }

    @PostMapping("/api/correos/asincrono")
    public ResponseEntity<Map<String, Object>> asincrono(@RequestParam(defaultValue = "500") long demoraMs) {
        correos.enviarEnCola(demoraMs);
        return ResponseEntity.accepted().body(Map.of("estado", "EN_COLA"));
    }

    @GetMapping("/api/correos/enviados")
    public Map<String, Object> enviados() {
        return Map.of("enviados", correos.enviados());
    }
}
