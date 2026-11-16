package cursos.inscripciones.interno;

import cursos.inscripciones.CursoCompletado;
import cursos.inscripciones.InscripcionesApi;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
class ServicioInscripciones implements InscripcionesApi {
    private final Map<String, Inscripcion> inscripciones = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher eventos;

    ServicioInscripciones(ApplicationEventPublisher eventos) { this.eventos = eventos; }

    @Override
    public void inscribir(String estudiante, String cursoId) {
        inscripciones.put(estudiante + "/" + cursoId, new Inscripcion(estudiante, cursoId));
    }

    @Override
    @Transactional
    public void completar(String estudiante, String cursoId) {
        Inscripcion inscripcion = inscripciones.get(estudiante + "/" + cursoId);
        if (inscripcion == null) throw new IllegalArgumentException("No está inscrito");
        inscripcion.finalizar();
        eventos.publishEvent(new CursoCompletado(estudiante, cursoId));   // no conoce a certificación
    }
}
