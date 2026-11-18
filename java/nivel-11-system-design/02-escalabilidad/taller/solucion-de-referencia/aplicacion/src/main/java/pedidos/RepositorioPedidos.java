package pedidos;

import java.math.BigDecimal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RepositorioPedidos extends JpaRepository<Pedido, Long> {

    @Query("select coalesce(sum(p.total), 0) from Pedido p where p.clienteId = :clienteId")
    BigDecimal totalDelCliente(long clienteId);

    long countByClienteId(long clienteId);
}
