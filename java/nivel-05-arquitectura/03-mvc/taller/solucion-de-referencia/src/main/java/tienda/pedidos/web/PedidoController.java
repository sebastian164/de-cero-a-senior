package tienda.pedidos.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tienda.pedidos.aplicacion.CancelarPedido;

/** Controlador delgado: entrada, delegación y salida. */
@RestController
@RequestMapping("/pedidos")
class PedidoController {

    private final CancelarPedido cancelarPedido;

    PedidoController(CancelarPedido cancelarPedido) { this.cancelarPedido = cancelarPedido; }

    @PostMapping("/{id}/cancelar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancelar(@PathVariable Long id) {
        cancelarPedido.ejecutar(id);
    }
}
