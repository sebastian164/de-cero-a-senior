package citas;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
class ServicioCitas {

    private final CitaRepository citas;
    private final ClaveIdempotenciaRepository claves;
    private final Clock reloj;

    ServicioCitas(CitaRepository citas, ClaveIdempotenciaRepository claves, Clock reloj) {
        this.citas = citas;
        this.claves = claves;
        this.reloj = reloj;
    }

    record Creada(Cita cita, boolean repetida) { }

    @Transactional
    Creada crear(String clave, CrearCitaRequest request) {
        String huella = huella(request);
        var previa = claves.findById(clave);
        if (previa.isPresent()) {
            if (!previa.get().getHuellaDelCuerpo().equals(huella))
                throw new ErrorDeApi.ReglaDeNegocio("clave-reutilizada", "Idempotency-Key reutilizada",
                        "La clave ya se usó con otro cuerpo");
            return new Creada(buscar(previa.get().getCitaId()), true);
        }
        if (!request.inicio().isAfter(LocalDateTime.now(reloj)))
            throw new ErrorDeApi.ReglaDeNegocio("cita-en-el-pasado", "Cita en el pasado",
                    "La cita debe ser en el futuro");
        if (citas.existsByMedicoIdAndInicioAndEstado(request.medicoId(), request.inicio(), EstadoCita.PROGRAMADA))
            throw new ErrorDeApi.Conflicto("El médico ya tiene una cita a esa hora");
        Cita cita = citas.save(new Cita(request.medicoId(), request.pacienteId(), request.pacienteNombre(),
                request.inicio(), request.motivo()));
        claves.saveAndFlush(new ClaveIdempotencia(clave, huella, cita.getId()));   // si otra petición ganó la carrera, la clave primaria falla aquí
        return new Creada(cita, false);
    }

    /** SHA-256 sobre una forma canónica del cuerpo: dos cuerpos distintos no comparten huella en la práctica. */
    static String huella(CrearCitaRequest r) {
        String canonico = String.join("\u0000", String.valueOf(r.medicoId()), String.valueOf(r.pacienteId()),
                String.valueOf(r.pacienteNombre()), String.valueOf(r.inicio()), String.valueOf(r.motivo()));
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonico.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional(readOnly = true)
    Cita buscar(Long id) {
        return citas.findById(id).orElseThrow(() -> new ErrorDeApi.NoEncontrada(id));
    }

    @Transactional(readOnly = true)
    Page<Cita> listar(Long medicoId, EstadoCita estado, LocalDate desde, Pageable pagina) {
        Specification<Cita> filtro = Specification.allOf(
                (r, q, cb) -> medicoId == null ? null : cb.equal(r.get("medicoId"), medicoId),
                (r, q, cb) -> estado == null ? null : cb.equal(r.get("estado"), estado),
                (r, q, cb) -> desde == null ? null : cb.greaterThanOrEqualTo(r.get("inicio"), desde.atStartOfDay()));
        return citas.findAll(filtro, pagina);
    }

    @Transactional
    void cancelar(Long id) {
        buscar(id).cancelar(LocalDateTime.now(reloj));
    }
}
