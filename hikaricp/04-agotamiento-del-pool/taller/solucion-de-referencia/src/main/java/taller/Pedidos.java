package taller;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface Pedidos extends JpaRepository<Pedido, Long> {

    @Query("select p from Pedido p left join fetch p.lineas where p.id = :id")
    Optional<Pedido> conLineas(Long id);
}
