package tienda.antes.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tienda.antes.model.Cliente;

public interface ClienteRepository extends JpaRepository<Cliente, Long> { }
