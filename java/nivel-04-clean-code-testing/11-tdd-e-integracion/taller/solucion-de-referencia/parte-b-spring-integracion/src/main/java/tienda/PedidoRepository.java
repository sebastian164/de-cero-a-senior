package tienda;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @Query("select p from Pedido p where p.cliente = :cliente and p.estado = tienda.EstadoPedido.PENDIENTE order by p.creadoEn")
    List<Pedido> findPendientesPorCliente(String cliente);
}
