package citas;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;

interface CitaRepository extends JpaRepository<Cita, Long>, JpaSpecificationExecutor<Cita> {
    boolean existsByMedicoIdAndInicioAndEstado(Long medicoId, LocalDateTime inicio, EstadoCita estado);
}

interface ClaveIdempotenciaRepository extends JpaRepository<ClaveIdempotencia, String> { }
