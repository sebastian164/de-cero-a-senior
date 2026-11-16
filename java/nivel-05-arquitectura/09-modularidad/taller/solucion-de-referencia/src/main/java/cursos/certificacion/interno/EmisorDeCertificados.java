package cursos.certificacion.interno;

import cursos.catalogo.CatalogoApi;
import cursos.certificacion.CertificacionApi;
import cursos.inscripciones.CursoCompletado;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
class EmisorDeCertificados implements CertificacionApi {
    private final CatalogoApi catalogo;                        // llamada directa a la API pública
    private final Map<String, List<String>> emitidos = new ConcurrentHashMap<>();

    EmisorDeCertificados(CatalogoApi catalogo) { this.catalogo = catalogo; }

    /** Reacciona a un hecho de otro módulo; no lo manipula. */
    @ApplicationModuleListener
    void alCompletar(CursoCompletado evento) {
        emitidos.computeIfAbsent(evento.estudiante(), e -> new ArrayList<>())
                .add("Certificado: " + catalogo.tituloDe(evento.cursoId()));
    }

    @Override
    public List<String> certificadosDe(String estudiante) { return List.copyOf(emitidos.getOrDefault(estudiante, List.of())); }
}
