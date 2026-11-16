package seg;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
class PedidoNoEncontrado extends RuntimeException {
    PedidoNoEncontrado(Long id) { super("Pedido " + id + " no existe"); }
}
