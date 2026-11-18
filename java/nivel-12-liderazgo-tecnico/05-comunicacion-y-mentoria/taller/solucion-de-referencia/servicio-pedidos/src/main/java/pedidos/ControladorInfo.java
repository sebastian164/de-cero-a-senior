package pedidos;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de apoyo para los talleres: configuración, sesión, lentitud y memoria. */
@RestController
public class ControladorInfo {
    private static final List<byte[]> RETENIDO = new ArrayList<>();

    private final String mensaje;
    private final String ambiente;

    public ControladorInfo(@Value("${app.mensaje:hola}") String mensaje,
                           @Value("${app.ambiente:local}") String ambiente) {
        this.mensaje = mensaje;
        this.ambiente = ambiente;
    }

    @GetMapping("/api/info")
    public Map<String, Object> info() throws Exception {
        return Map.of("mensaje", mensaje, "ambiente", ambiente,
                "instancia", InetAddress.getLocalHost().getHostName(),
                "memoriaMaximaMb", Runtime.getRuntime().maxMemory() / (1024 * 1024));
    }

    @GetMapping("/api/visitas")
    public Map<String, Object> visitas(HttpSession sesion) throws Exception {
        Integer visitas = (Integer) sesion.getAttribute("visitas");
        visitas = visitas == null ? 1 : visitas + 1;
        sesion.setAttribute("visitas", visitas);
        return Map.of("visitas", visitas, "instancia", InetAddress.getLocalHost().getHostName());
    }

    @GetMapping("/api/lento")
    public Map<String, Object> lento(@RequestParam(defaultValue = "1000") long ms) throws InterruptedException {
        Thread.sleep(ms);
        return Map.of("esperado", ms);
    }

    @PostMapping("/api/memoria")
    public Map<String, Object> memoria(@RequestParam int mb) {
        for (int i = 0; i < mb; i++) {
            byte[] bloque = new byte[1024 * 1024];
            for (int j = 0; j < bloque.length; j += 4096) {
                bloque[j] = 1; // toca cada página para que cuente en la memoria del proceso
            }
            RETENIDO.add(bloque);
        }
        return Map.of("retenidoMb", RETENIDO.size());
    }

    @DeleteMapping("/api/memoria")
    public Map<String, Object> liberar() {
        RETENIDO.clear();
        return Map.of("retenidoMb", 0);
    }
}
