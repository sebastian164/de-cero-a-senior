package obs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class Controlador {
    private static final Logger log = LoggerFactory.getLogger(Controlador.class);
    private final ConfirmarPedido confirmar;
    private final Pasarela pasarela;

    Controlador(ConfirmarPedido confirmar, Pasarela pasarela) {
        this.confirmar = confirmar;
        this.pasarela = pasarela;
    }

    /** Punto de entrada (servicio A). */
    @GetMapping("/a/pedido/{id}")
    String pedido(@PathVariable long id) { return confirmar.ejecutar(id); }

    /** Servicio B. */
    @GetMapping("/b/cobro/{id}")
    String cobro(@PathVariable long id) {
        log.info("Cobrando pedidoId={}", id);
        pasarela.cobrar(id);
        return "cobrado";
    }

    /** Petición lenta para probar el apagado ordenado. */
    @GetMapping("/lento")
    String lento(@RequestParam(defaultValue = "2000") long ms) throws InterruptedException {
        Thread.sleep(ms);
        return "terminada";
    }
}
