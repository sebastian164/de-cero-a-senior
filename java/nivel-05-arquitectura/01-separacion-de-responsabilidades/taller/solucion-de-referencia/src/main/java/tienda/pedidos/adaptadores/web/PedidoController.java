package tienda.pedidos.adaptadores.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tienda.pedidos.aplicacion.CrearPedido;

/** Presentación: traduce HTTP <-> aplicación. Sin reglas, sin SQL. */
@RestController
@RequestMapping("/pedidos")
class PedidoController {

    private final CrearPedido crearPedido;

    PedidoController(CrearPedido crearPedido) {
        this.crearPedido = crearPedido;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    PedidoResponse crear(@Valid @RequestBody CrearPedidoRequest request) {
        return PedidoResponse.de(crearPedido.ejecutar(request.aComando()));
    }
}
