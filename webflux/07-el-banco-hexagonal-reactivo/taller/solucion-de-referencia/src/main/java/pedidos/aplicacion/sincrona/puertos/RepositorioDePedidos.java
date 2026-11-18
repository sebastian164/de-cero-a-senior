package pedidos.aplicacion.sincrona.puertos;

import java.util.Optional;
import tienda.dominio.EstadoDePedido;
import tienda.dominio.Pedido;

public interface RepositorioDePedidos {
    Optional<Pedido> buscar(String id);
    /** Guarda el estado del pedido solo si en la base sigue como se leyó; si otro lo cambió, lanza ConflictoDeConcurrencia. */
    void guardar(Pedido pedido, EstadoDePedido estadoLeido);
}
