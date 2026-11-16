package taller.state;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public class Main {

    public static void main(String[] args) {
        LocalDate inicio = LocalDate.of(2025, 7, 1);
        for (Solicitud s : new Solicitud[] {new SolicitudConEnum(inicio), new SolicitudConState(inicio)}) {
            String nombre = s.getClass().getSimpleName();
            s.enviar();
            s.aprobar();
            System.out.println(nombre + ": " + s.estado());
            s.iniciarDisfrute(LocalDate.of(2025, 7, 1));
            try {
                s.cancelar(LocalDate.of(2025, 7, 2));
            } catch (IllegalStateException e) {
                System.out.println(nombre + ": rechazado -> " + e.getMessage());
            }
        }
        System.out.println("¿Desde dónde se llega a APROBADA? " + EstadoVacaciones.origenesDe(EstadoVacaciones.APROBADA));
    }
}

/** Contrato común para probar las dos versiones con las mismas pruebas. */
interface Solicitud {
    void enviar();
    void aprobar();
    void rechazar();
    void corregir();                     // rechazada -> borrador
    void cancelar(LocalDate hoy);        // aprobada y antes de la fecha de inicio
    void iniciarDisfrute(LocalDate hoy); // aprobada y en la fecha de inicio o después
    String estado();
}

// ===================== Versión 1: enum + tabla de transiciones =====================

enum EstadoVacaciones {
    BORRADOR, ENVIADA, APROBADA, RECHAZADA, CANCELADA, EN_DISFRUTE;

    enum Accion { ENVIAR, APROBAR, RECHAZAR, CORREGIR, CANCELAR, INICIAR_DISFRUTE }

    /** Toda la máquina en una tabla. */
    static final Map<EstadoVacaciones, Map<Accion, EstadoVacaciones>> TRANSICIONES = Map.of(
            BORRADOR, Map.of(Accion.ENVIAR, ENVIADA),
            ENVIADA, Map.of(Accion.APROBAR, APROBADA, Accion.RECHAZAR, RECHAZADA),
            APROBADA, Map.of(Accion.CANCELAR, CANCELADA, Accion.INICIAR_DISFRUTE, EN_DISFRUTE),
            RECHAZADA, Map.of(Accion.CORREGIR, BORRADOR),
            CANCELADA, Map.of(),
            EN_DISFRUTE, Map.of());

    EstadoVacaciones aplicar(Accion accion) {
        EstadoVacaciones destino = TRANSICIONES.get(this).get(accion);
        if (destino == null) {
            throw new IllegalStateException("No se puede " + accion.name().toLowerCase().replace('_', ' ') + " una solicitud " + this);
        }
        return destino;
    }

    /** La pregunta del paso 3 se responde consultando la tabla. */
    static Set<EstadoVacaciones> origenesDe(EstadoVacaciones destino) {
        Set<EstadoVacaciones> r = EnumSet.noneOf(EstadoVacaciones.class);
        TRANSICIONES.forEach((origen, salidas) -> { if (salidas.containsValue(destino)) r.add(origen); });
        return r;
    }
}

final class SolicitudConEnum implements Solicitud {
    private final LocalDate fechaInicio;
    private EstadoVacaciones estado = EstadoVacaciones.BORRADOR;

    SolicitudConEnum(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }

    public void enviar() { estado = estado.aplicar(EstadoVacaciones.Accion.ENVIAR); }
    public void aprobar() { estado = estado.aplicar(EstadoVacaciones.Accion.APROBAR); }
    public void rechazar() { estado = estado.aplicar(EstadoVacaciones.Accion.RECHAZAR); }
    public void corregir() { estado = estado.aplicar(EstadoVacaciones.Accion.CORREGIR); }

    // Las reglas con condición (fecha) quedan fuera de la tabla: son guardas.
    public void cancelar(LocalDate hoy) {
        EstadoVacaciones destino = estado.aplicar(EstadoVacaciones.Accion.CANCELAR);
        if (!hoy.isBefore(fechaInicio)) throw new IllegalStateException("Solo se cancela antes de la fecha de inicio");
        estado = destino;
    }
    public void iniciarDisfrute(LocalDate hoy) {
        EstadoVacaciones destino = estado.aplicar(EstadoVacaciones.Accion.INICIAR_DISFRUTE);
        if (hoy.isBefore(fechaInicio)) throw new IllegalStateException("Las vacaciones aún no empiezan");
        estado = destino;
    }
    public String estado() { return estado.name(); }
}

// ===================== Versión 2: patrón State =====================

final class SolicitudConState implements Solicitud {
    private final LocalDate fechaInicio;
    private Estado estado = new Borrador();

    SolicitudConState(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }

    public void enviar() { estado = estado.enviar(); }
    public void aprobar() { estado = estado.aprobar(); }
    public void rechazar() { estado = estado.rechazar(); }
    public void corregir() { estado = estado.corregir(); }
    public void cancelar(LocalDate hoy) { estado = estado.cancelar(this, hoy); }
    public void iniciarDisfrute(LocalDate hoy) { estado = estado.iniciarDisfrute(this, hoy); }
    public String estado() { return estado.nombre(); }
    LocalDate fechaInicio() { return fechaInicio; }

    /** Por defecto, todo está prohibido; cada estado sobrescribe lo que permite. */
    abstract static class Estado {
        abstract String nombre();
        Estado enviar() { throw prohibido("enviar"); }
        Estado aprobar() { throw prohibido("aprobar"); }
        Estado rechazar() { throw prohibido("rechazar"); }
        Estado corregir() { throw prohibido("corregir"); }
        Estado cancelar(SolicitudConState s, LocalDate hoy) { throw prohibido("cancelar"); }
        Estado iniciarDisfrute(SolicitudConState s, LocalDate hoy) { throw prohibido("iniciar disfrute"); }
        IllegalStateException prohibido(String accion) {
            return new IllegalStateException("No se puede " + accion + " una solicitud " + nombre());
        }
    }

    static final class Borrador extends Estado {
        String nombre() { return "BORRADOR"; }
        @Override Estado enviar() { return new Enviada(); }
    }

    static final class Enviada extends Estado {
        String nombre() { return "ENVIADA"; }
        @Override Estado aprobar() { return new Aprobada(); }
        @Override Estado rechazar() { return new Rechazada(); }
    }

    static final class Aprobada extends Estado {
        String nombre() { return "APROBADA"; }
        @Override Estado cancelar(SolicitudConState s, LocalDate hoy) {
            if (!hoy.isBefore(s.fechaInicio())) throw new IllegalStateException("Solo se cancela antes de la fecha de inicio");
            return new Cancelada();
        }
        @Override Estado iniciarDisfrute(SolicitudConState s, LocalDate hoy) {
            if (hoy.isBefore(s.fechaInicio())) throw new IllegalStateException("Las vacaciones aún no empiezan");
            return new EnDisfrute();
        }
    }

    static final class Rechazada extends Estado {
        String nombre() { return "RECHAZADA"; }
        @Override Estado corregir() { return new Borrador(); }
    }

    static final class Cancelada extends Estado {
        String nombre() { return "CANCELADA"; }
    }

    /** Paso 4: estado nuevo. No permite cancelar: sobrescribe cancelar() para rechazarlo con un mensaje propio. */
    static final class EnDisfrute extends Estado {
        String nombre() { return "EN_DISFRUTE"; }
        @Override Estado cancelar(SolicitudConState s, LocalDate hoy) {
            throw new IllegalStateException("No se pueden cancelar vacaciones en disfrute");
        }
    }
}
