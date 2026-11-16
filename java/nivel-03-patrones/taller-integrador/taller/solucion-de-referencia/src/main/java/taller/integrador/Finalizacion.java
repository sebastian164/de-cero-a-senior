package taller.integrador;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Requisito 5: al completar un curso se emite un certificado (parte de la operación: si no hay
 * certificado, el curso no está completado) y luego se notifica a quien le interese (Observer):
 * aviso al estudiante, estadísticas, recomendaciones y, en la Parte 3, el perfil público.
 */
final class Finalizacion {
    private Finalizacion() { }

    record Certificado(String codigo, String estudiante, String curso, LocalDate fecha) { }
    /**
     * El evento es un hecho inmutable con los datos que usan los oyentes, no la entidad: así ningún
     * oyente puede cambiar el estado de la inscripción (error común de la lección de Observer).
     */
    record CursoCompletado(String inscripcionId, String estudianteId, String estudianteNombre, String estudianteCorreo,
                           boolean autorizaPerfilPublico, String cursoId, String cursoNombre, Certificado certificado) {
        static CursoCompletado de(Inscripcion i, Certificado c) {
            Estudiante e = i.estudiante();
            return new CursoCompletado(i.id(), e.id(), e.nombre(), e.correo(), e.autorizaPerfilPublico(),
                    i.curso().id(), i.curso().nombre(), c);
        }
    }

    interface Oyente { void alCompletar(CursoCompletado evento); }

    static final class Publicador {
        private final List<Oyente> oyentes = new CopyOnWriteArrayList<>();
        private final Consumer<String> errores;
        Publicador(Consumer<String> errores) { this.errores = errores; }
        void suscribir(Oyente o) { oyentes.add(o); }
        void publicar(CursoCompletado e) {
            for (Oyente o : oyentes) {
                try { o.alCompletar(e); }
                catch (RuntimeException ex) { errores.accept("Falló " + o.getClass().getSimpleName() + ": " + ex.getMessage()); }
            }
        }
    }

    static final class Certificados {
        private int consecutivo;
        Certificado emitir(Inscripcion i, LocalDate hoy) {
            return new Certificado("CERT-" + (++consecutivo), i.estudiante().nombre(), i.curso().nombre(), hoy);
        }
    }

    static final class AvisoEstudiante implements Oyente {
        final List<String> enviados = new ArrayList<>();
        public void alCompletar(CursoCompletado e) {
            enviados.add("Para " + e.estudianteCorreo() + ": ¡completaste " + e.cursoNombre()
                    + "! Certificado " + e.certificado().codigo());
        }
    }

    static final class Estadisticas implements Oyente {
        final Map<String, Integer> completadosPorCurso = new HashMap<>();
        public void alCompletar(CursoCompletado e) { completadosPorCurso.merge(e.cursoId(), 1, Integer::sum); }
    }

    static final class Recomendaciones implements Oyente {
        final Map<String, List<String>> desbloqueadas = new HashMap<>();
        private final Map<String, List<String>> siguientes;
        Recomendaciones(Map<String, List<String>> siguientes) { this.siguientes = siguientes; }
        public void alCompletar(CursoCompletado e) {
            desbloqueadas.put(e.estudianteId(), siguientes.getOrDefault(e.cursoId(), List.of()));
        }
    }

    /** Parte 3, cambio 5: oyente nuevo; la condición de autorización vive dentro de él. */
    static final class PerfilPublico implements Oyente {
        final List<String> publicaciones = new ArrayList<>();
        public void alCompletar(CursoCompletado e) {
            if (e.autorizaPerfilPublico()) {
                publicaciones.add(e.estudianteNombre() + " completó " + e.cursoNombre());
            }
        }
    }
}
