package taller.integrador;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Requisito 3: estados de la inscripción. SIN patrón State: los estados solo cambian qué transiciones
 * se permiten, no tienen comportamiento propio. Un enum con tabla de transiciones basta y deja ver
 * toda la máquina en un lugar.
 */
enum EstadoInscripcion {
    PENDIENTE_PAGO, EN_PRUEBA, ACTIVA, SUSPENDIDA, FINALIZADA, REEMBOLSADA;

    enum Evento { INICIAR_PRUEBA, PAGAR, SUSPENDER, REACTIVAR, FINALIZAR, REEMBOLSAR, VENCER_PRUEBA }

    static final Map<EstadoInscripcion, Map<Evento, EstadoInscripcion>> TRANSICIONES = Map.of(
            PENDIENTE_PAGO, Map.of(Evento.PAGAR, ACTIVA, Evento.INICIAR_PRUEBA, EN_PRUEBA),
            // Parte 3, cambio 4: una fila nueva y una entrada en PENDIENTE_PAGO.
            EN_PRUEBA, Map.of(Evento.PAGAR, ACTIVA, Evento.VENCER_PRUEBA, PENDIENTE_PAGO),
            ACTIVA, Map.of(Evento.SUSPENDER, SUSPENDIDA, Evento.FINALIZAR, FINALIZADA, Evento.REEMBOLSAR, REEMBOLSADA),
            SUSPENDIDA, Map.of(Evento.REACTIVAR, ACTIVA, Evento.REEMBOLSAR, REEMBOLSADA),
            FINALIZADA, Map.of(),
            REEMBOLSADA, Map.of());

    EstadoInscripcion aplicar(Evento e) {
        EstadoInscripcion destino = TRANSICIONES.get(this).get(e);
        if (destino == null) throw new IllegalStateException("No se puede " + e + " una inscripción " + this);
        return destino;
    }

    static Set<EstadoInscripcion> origenesDe(EstadoInscripcion destino) {
        Set<EstadoInscripcion> r = EnumSet.noneOf(EstadoInscripcion.class);
        TRANSICIONES.forEach((o, s) -> { if (s.containsValue(destino)) r.add(o); });
        return r;
    }
}

final class Inscripcion {
    static final int DIAS_PRUEBA = 7;

    private final String id;
    private final Estudiante estudiante;
    private final Catalogo.Curso curso;
    private EstadoInscripcion estado = EstadoInscripcion.PENDIENTE_PAGO;
    private LocalDate inicioPrueba;
    private final Set<String> leccionesCompletadas = new HashSet<>();

    Inscripcion(String id, Estudiante estudiante, Catalogo.Curso curso) {
        this.id = id;
        this.estudiante = estudiante;
        this.curso = curso;
    }

    void aplicar(EstadoInscripcion.Evento e) { estado = estado.aplicar(e); }

    void iniciarPrueba(LocalDate hoy) {
        aplicar(EstadoInscripcion.Evento.INICIAR_PRUEBA);
        inicioPrueba = hoy;
    }

    /** ¿Da acceso hoy? La prueba vale 7 días contando el de inicio. */
    boolean daAcceso(LocalDate hoy) {
        return switch (estado) {
            case ACTIVA -> true;
            case EN_PRUEBA -> hoy.isBefore(inicioPrueba.plusDays(DIAS_PRUEBA));
            default -> false;
        };
    }

    void marcarCompletada(String leccionId) { leccionesCompletadas.add(leccionId); }
    boolean completo() { return leccionesCompletadas.size() == curso.lecciones().size(); }
    Set<String> completadas() { return Set.copyOf(leccionesCompletadas); }

    String id() { return id; }
    Estudiante estudiante() { return estudiante; }
    Catalogo.Curso curso() { return curso; }
    EstadoInscripcion estado() { return estado; }
}

record Estudiante(String id, String nombre, String correo, String pais, boolean autorizaPerfilPublico) { }
