package taller.integrador;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Requisito 7: guardar y retomar el progreso de un examen. SIN Memento: el progreso es un valor
 * inmutable (record con copia del mapa) que se guarda y se recupera; no hay un originador mutable
 * cuyo interior haya que proteger ni operación de deshacer.
 */
final class Examenes {
    private Examenes() { }

    record Progreso(String inscripcionId, String examenId, Map<Integer, String> respuestas, int preguntaActual) {
        Progreso { respuestas = Map.copyOf(respuestas); }

        static Progreso nuevo(String inscripcionId, String examenId) {
            return new Progreso(inscripcionId, examenId, Map.of(), 0);
        }

        Progreso responder(int pregunta, String respuesta) {
            Map<Integer, String> r = new HashMap<>(respuestas);
            r.put(pregunta, respuesta);
            return new Progreso(inscripcionId, examenId, r, pregunta + 1);
        }
    }

    static final class Repositorio {
        private final Map<String, Progreso> guardados = new HashMap<>();
        void guardar(Progreso p) { guardados.put(p.inscripcionId() + "/" + p.examenId(), p); }
        Optional<Progreso> retomar(String inscripcionId, String examenId) {
            return Optional.ofNullable(guardados.get(inscripcionId + "/" + examenId));
        }
    }
}
