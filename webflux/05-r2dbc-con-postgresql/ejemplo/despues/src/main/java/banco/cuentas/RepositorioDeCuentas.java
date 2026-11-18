package banco.cuentas;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

/** Un repositorio reactivo: Spring Data escribe la implementación y cada método devuelve Mono o Flux. */
public interface RepositorioDeCuentas extends ReactiveCrudRepository<Cuenta, String> {
}
