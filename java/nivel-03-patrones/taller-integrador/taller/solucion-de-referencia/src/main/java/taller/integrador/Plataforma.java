package taller.integrador;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Servicio de aplicación: coordina los casos de uso (es la "fachada" natural de un servicio de
 * aplicación, no un patrón agregado a propósito). No contiene reglas: las delega.
 */
final class Plataforma {
    private final Promociones.Cotizador cotizador;
    private final Pagos.PasarelasPorPais pasarelas;
    private final Entrega.Cadenas entrega;
    private final Finalizacion.Certificados certificados;
    private final Finalizacion.Publicador publicador;
    private final Examenes.Repositorio examenes;
    private final Map<String, Inscripcion> inscripciones = new HashMap<>();
    private int consecutivo;

    Plataforma(Promociones.Cotizador cotizador, Pagos.PasarelasPorPais pasarelas, Entrega.Cadenas entrega,
               Finalizacion.Certificados certificados, Finalizacion.Publicador publicador, Examenes.Repositorio examenes) {
        this.cotizador = cotizador; this.pasarelas = pasarelas; this.entrega = entrega;
        this.certificados = certificados; this.publicador = publicador; this.examenes = examenes;
    }

    Inscripcion inscribir(Estudiante e, Catalogo.Curso c) {
        Inscripcion i = new Inscripcion("INS-" + (++consecutivo), e, c);
        inscripciones.put(i.id(), i);
        return i;
    }

    Promociones.Cotizacion cotizar(List<Catalogo.Curso> cursos, Optional<String> cupon, LocalDate hoy) {
        return cotizador.cotizar(new Promociones.Carrito(cursos, cupon, hoy));
    }

    /** Paga en un solo cobro todas las inscripciones del carrito, con el precio cotizado. */
    Pagos.ResultadoPago pagar(List<Inscripcion> carrito, Optional<String> cupon, LocalDate hoy) {
        Estudiante e = carrito.get(0).estudiante();
        Promociones.Cotizacion cot = cotizar(carrito.stream().map(Inscripcion::curso).toList(), cupon, hoy);
        Pagos.ResultadoPago r = pasarelas.cobrar(e.pais(), e.id(), cot.neto());
        if (r.aprobado()) carrito.forEach(i -> i.aplicar(EstadoInscripcion.Evento.PAGAR));
        return r;
    }

    Entrega.Resultado verLeccion(String token, Inscripcion i, String leccionId, String dispositivo, LocalDate hoy) {
        return entrega.entregar(new Entrega.Solicitud(token, i, leccionId, dispositivo, hoy));
    }

    /** Consumir = ver y marcar completada. Si con esto se completa el curso, se certifica. */
    Optional<Finalizacion.Certificado> completarLeccion(String token, Inscripcion i, String leccionId, String dispositivo, LocalDate hoy) {
        Entrega.Resultado r = verLeccion(token, i, leccionId, dispositivo, hoy);
        if (!r.entregada()) throw new IllegalStateException(r.detalle());
        i.marcarCompletada(leccionId);
        if (!i.completo()) return Optional.empty();
        i.aplicar(EstadoInscripcion.Evento.FINALIZAR);
        Finalizacion.Certificado cert = certificados.emitir(i, hoy);
        publicador.publicar(Finalizacion.CursoCompletado.de(i, cert));
        return Optional.of(cert);
    }

    // Ciclo de vida administrativo: la tabla de EstadoInscripcion decide si la transición vale.
    void suspender(Inscripcion i) { i.aplicar(EstadoInscripcion.Evento.SUSPENDER); }
    void reactivar(Inscripcion i) { i.aplicar(EstadoInscripcion.Evento.REACTIVAR); }
    void reembolsar(Inscripcion i) { i.aplicar(EstadoInscripcion.Evento.REEMBOLSAR); }

    void guardarExamen(Examenes.Progreso p) { examenes.guardar(p); }
    Optional<Examenes.Progreso> retomarExamen(Inscripcion i, String examenId) { return examenes.retomar(i.id(), examenId); }
}
