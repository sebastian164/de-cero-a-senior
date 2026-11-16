package tienda.pedidos.infraestructura;

import org.springframework.stereotype.Component;
import tienda.pedidos.dominio.Pedido;
import tienda.pedidos.dominio.RepositorioPedidos;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RepositorioPedidosEnMemoria implements RepositorioPedidos {
    private final Map<Long, Pedido> datos = new ConcurrentHashMap<>();

    @Override public Optional<Pedido> buscar(long id) { return Optional.ofNullable(datos.get(id)); }
    @Override public void guardar(Pedido pedido) { datos.put(pedido.id(), pedido); }
}
