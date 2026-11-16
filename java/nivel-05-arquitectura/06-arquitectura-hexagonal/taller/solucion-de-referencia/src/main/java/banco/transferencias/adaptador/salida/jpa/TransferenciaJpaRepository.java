package banco.transferencias.adaptador.salida.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.UUID;

interface TransferenciaJpaRepository extends JpaRepository<TransferenciaJpaEntity, UUID> {
    @Query("select coalesce(sum(t.centavos), 0) from TransferenciaJpaEntity t where t.origen = :origen and t.aceptada = true and t.fecha >= :desde and t.fecha < :hasta")
    long sumaAceptadas(String origen, LocalDateTime desde, LocalDateTime hasta);
}
