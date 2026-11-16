package tienda.pedidos.aplicacion;

import tienda.pedidos.dominio.Pedido;

import java.util.Optional;

public interface RepositorioPedidos {
    Optional<Pedido> buscar(long id);
    void guardar(Pedido pedido);
}
