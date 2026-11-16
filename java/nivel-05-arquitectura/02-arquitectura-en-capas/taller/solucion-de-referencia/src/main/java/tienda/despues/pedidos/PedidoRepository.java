package tienda.despues.pedidos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {
    @Query("select coalesce(sum(p.total), 0) from Pedido p where p.cliente.id = :clienteId and p.estado in :estados")
    long sumaDe(Long clienteId, Collection<EstadoPedido> estados);
}
