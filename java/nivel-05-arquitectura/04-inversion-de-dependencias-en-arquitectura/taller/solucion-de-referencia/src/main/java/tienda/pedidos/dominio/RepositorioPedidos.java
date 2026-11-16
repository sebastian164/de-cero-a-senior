package tienda.pedidos.dominio;

import java.util.Optional;

public interface RepositorioPedidos {
    Optional<Pedido> buscar(long id);
    void guardar(Pedido pedido);
}
