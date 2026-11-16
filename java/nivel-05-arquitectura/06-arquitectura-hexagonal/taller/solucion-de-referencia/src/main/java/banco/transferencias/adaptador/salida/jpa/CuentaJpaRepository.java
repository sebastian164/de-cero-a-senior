package banco.transferencias.adaptador.salida.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

interface CuentaJpaRepository extends JpaRepository<CuentaJpaEntity, String> { }
