package obs;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Solo para las pruebas: vive en src/test para que no exista en el artefacto de producción. */
@RestController
class ControlExterno {
    @PostMapping("/test/externo/caer")
    void caer() { ExternoSalud.arriba.set(false); }

    @PostMapping("/test/externo/subir")
    void subir() { ExternoSalud.arriba.set(true); }
}
