package oficina.reservas.adaptadores.persistencia.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

interface ReservaJpaRepository extends JpaRepository<ReservaJpaEntity, UUID> {
    @Query("select r from ReservaJpaEntity r where r.sala = :sala and r.estado = 'ACTIVA' and r.inicio < :hasta and r.fin > :desde")
    List<ReservaJpaEntity> activasQueTocan(String sala, LocalDateTime desde, LocalDateTime hasta);
}
