package transacciones;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface InventarioRepositorio extends JpaRepository<Inventario, Long> {

    @Modifying
    @Query("update Inventario i set i.stock = i.stock - :n where i.id = :id and i.stock >= :n")
    int descontarAtomico(Long id, int n);
}
