package tienda.aplicacion.puertos.salida;

import java.util.List;
import java.util.Optional;
import tienda.dominio.Pedido;

public interface RepositorioDePedidos {

    String siguienteId();

    void guardar(Pedido pedido);

    Optional<Pedido> buscar(String pedidoId);

    /** Los pedidos de un cliente, del más antiguo al más reciente. */
    List<Pedido> deCliente(String cliente);
}
