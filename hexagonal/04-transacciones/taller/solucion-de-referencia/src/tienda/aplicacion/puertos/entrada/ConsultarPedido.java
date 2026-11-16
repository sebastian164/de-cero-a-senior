package tienda.aplicacion.puertos.entrada;

import java.util.Optional;
import tienda.aplicacion.VistaDePedido;

public interface ConsultarPedido {

    Optional<VistaDePedido> porId(String pedidoId);
}
