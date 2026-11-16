package indices;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PedidoRepositorio extends JpaRepository<Pedido, Long> {

    // Consulta derivada: parece inocente, pero Spring Data la traduce con upper(...) en los dos lados.
    List<Pedido> findByCorreoIgnoreCase(String correo);
}
