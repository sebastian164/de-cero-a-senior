package tienda;

import auditoria.ClienteAuditoria;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tienda.config.PagosProperties;

import java.util.Map;

@RestController
class ConfigController {
    private final PagosProperties pagos;
    private final ClienteAuditoria auditoria;

    ConfigController(PagosProperties pagos, ClienteAuditoria auditoria) { this.pagos = pagos; this.auditoria = auditoria; }

    @GetMapping("/config")
    Map<String, Object> config() {
        auditoria.registrar("anonimo", "consultó la configuración");
        return Map.of("reintentos", pagos.reintentos(), "tiempoLimite", pagos.tiempoLimite().toString(),
                "moneda", pagos.moneda(), "auditoria", auditoria.url());
    }
}
