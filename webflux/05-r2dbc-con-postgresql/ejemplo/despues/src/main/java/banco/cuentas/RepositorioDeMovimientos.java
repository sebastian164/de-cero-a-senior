package banco.cuentas;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface RepositorioDeMovimientos extends ReactiveCrudRepository<Movimiento, Long> {

    Flux<Movimiento> findByCuentaIdOrderById(String cuentaId);   // la consulta sale del nombre del método
}
