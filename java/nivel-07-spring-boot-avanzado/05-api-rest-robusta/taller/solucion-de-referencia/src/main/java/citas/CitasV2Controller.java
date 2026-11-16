package citas;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** v2 convive con v1 sobre el mismo servicio: solo cambia la representación. */
@RestController
@RequestMapping("/v2/citas")
class CitasV2Controller {

    private final ServicioCitas servicio;

    CitasV2Controller(ServicioCitas servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/{id}")
    CitaV2 buscar(@PathVariable Long id) {
        return CitaV2.de(servicio.buscar(id));
    }

    @GetMapping
    Page<CitaV2> listar(@RequestParam(required = false) Long medicoId,
                        @PageableDefault(size = 20, sort = "inicio") Pageable pagina) {
        return servicio.listar(medicoId, null, null, pagina).map(CitaV2::de);
    }
}
