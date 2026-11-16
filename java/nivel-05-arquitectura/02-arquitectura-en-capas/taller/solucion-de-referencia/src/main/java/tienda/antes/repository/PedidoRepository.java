package tienda.antes.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tienda.antes.model.Pedido;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {
    // Lógica de negocio escondida en SQL: qué estados cuentan como deuda
    @Query("select coalesce(sum(p.total), 0) from Pedido p where p.cliente.id = :clienteId and p.estado in ('CONFIRMADO', 'DESPACHADO')")
    long deudaDe(Long clienteId);
}
