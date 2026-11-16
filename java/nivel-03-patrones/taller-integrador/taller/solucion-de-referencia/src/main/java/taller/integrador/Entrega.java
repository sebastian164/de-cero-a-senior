package taller.integrador;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Requisito 6: antes de entregar una lección se verifica una serie de condiciones; cualquiera corta.
 * Chain of Responsibility en variante tubería: cada verificación es un eslabón con nombre, se
 * prueban por separado y el orden se ve en un solo lugar.
 * Los eslabones con efectos (registrar dispositivo, consumir cupo) solo los aplican cuando el resto
 * de la cadena terminó con éxito: un rechazo posterior no deja nada registrado.
 */
final class Entrega {
    private Entrega() { }

    record Solicitud(String token, Inscripcion inscripcion, String leccionId, String dispositivo, LocalDate hoy) { }

    record Resultado(boolean entregada, String detalle) {
        static Resultado denegada(String motivo) { return new Resultado(false, motivo); }
    }

    interface Verificacion {
        Resultado verificar(Solicitud s, Cadena siguiente);
    }

    interface Cadena { Resultado continuar(Solicitud s); }

    static final class Sesiones {
        private final Map<String, String> tokens = new HashMap<>();
        void abrir(String token, String estudianteId) { tokens.put(token, estudianteId); }
        boolean valida(String token, String estudianteId) { return estudianteId.equals(tokens.get(token)); }
    }

    static final class SesionValida implements Verificacion {
        private final Sesiones sesiones;
        SesionValida(Sesiones s) { this.sesiones = s; }
        public Resultado verificar(Solicitud s, Cadena c) {
            return sesiones.valida(s.token(), s.inscripcion().estudiante().id()) ? c.continuar(s) : Resultado.denegada("Sesión inválida");
        }
    }

    static final class InscripcionActiva implements Verificacion {
        public Resultado verificar(Solicitud s, Cadena c) {
            return s.inscripcion().daAcceso(s.hoy()) ? c.continuar(s)
                    : Resultado.denegada("Inscripción sin acceso (" + s.inscripcion().estado() + ")");
        }
    }

    /** Una lección se desbloquea al completar la anterior del curso. */
    static final class LeccionDesbloqueada implements Verificacion {
        public Resultado verificar(Solicitud s, Cadena c) {
            List<Catalogo.Leccion> todas = s.inscripcion().curso().lecciones();
            int i = todas.indexOf(s.inscripcion().curso().leccion(s.leccionId()));
            if (i == 0 || s.inscripcion().completadas().contains(todas.get(i - 1).id())) return c.continuar(s);
            return Resultado.denegada("Lección bloqueada: completa antes '" + todas.get(i - 1).titulo() + "'");
        }
    }

    static final class LimiteDispositivos implements Verificacion {
        private final int maximo;
        private final Map<String, Set<String>> porEstudiante = new HashMap<>();
        LimiteDispositivos(int maximo) { this.maximo = maximo; }
        public Resultado verificar(Solicitud s, Cadena c) {
            Set<String> d = porEstudiante.computeIfAbsent(s.inscripcion().estudiante().id(), k -> new HashSet<>());
            if (!d.contains(s.dispositivo()) && d.size() >= maximo) return Resultado.denegada("Límite de " + maximo + " dispositivos");
            Resultado r = c.continuar(s);
            if (r.entregada()) d.add(s.dispositivo());   // se registra solo si toda la cadena entregó
            return r;
        }
    }

    /** Parte 3, cambio 2: un eslabón nuevo para el cupo de los talleres en vivo. */
    static final class CupoTallerEnVivo implements Verificacion {
        private final Map<String, Set<String>> inscritos = new HashMap<>();
        public Resultado verificar(Solicitud s, Cadena c) {
            if (s.inscripcion().curso().leccion(s.leccionId()) instanceof Catalogo.TallerEnVivo t) {
                Set<String> cupos = inscritos.computeIfAbsent(t.id(), k -> new HashSet<>());
                String est = s.inscripcion().estudiante().id();
                if (!cupos.contains(est) && cupos.size() >= t.cupo()) return Resultado.denegada("Taller en vivo sin cupo");
                Resultado r = c.continuar(s);
                if (r.entregada()) cupos.add(est);           // se consume solo si toda la cadena entregó
                return r;
            }
            return c.continuar(s);
        }
    }

    static final class Cadenas {
        private final List<Verificacion> verificaciones;
        Cadenas(List<Verificacion> v) { this.verificaciones = List.copyOf(v); }

        Resultado entregar(Solicitud s) { return desde(0).continuar(s); }

        private Cadena desde(int i) {
            if (i == verificaciones.size()) {
                return s -> new Resultado(true, "Entregada: " + Catalogo.describir(s.inscripcion().curso().leccion(s.leccionId())));
            }
            return s -> verificaciones.get(i).verificar(s, desde(i + 1));
        }
    }
}
