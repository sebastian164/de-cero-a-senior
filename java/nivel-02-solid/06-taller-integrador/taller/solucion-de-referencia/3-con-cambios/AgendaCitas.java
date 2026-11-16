import java.time.LocalDateTime;
import java.util.List;

/** Caso de uso: registrar una cita. Reglas de negocio sin archivos ni correo. */
public final class AgendaCitas {

    private final RepositorioCitas repositorio;
    private final Notificador notificador;

    public AgendaCitas(RepositorioCitas repositorio, Notificador notificador) {
        this.repositorio = repositorio;
        this.notificador = notificador;
    }

    static final int CITAS_PARA_DESCUENTO = 5;

    /** Cambio 5: 10 % para dueños con más de cinco citas en el año (contando las ya registradas). */
    private long precio(Dueno dueno, TipoCita tipo, LocalDateTime fecha) {
        int previas = repositorio.deDuenoEnAnio(dueno.correo(), fecha.getYear()).size();
        return previas > CITAS_PARA_DESCUENTO ? tipo.precio() * 90 / 100 : tipo.precio();
    }

    public Cita registrar(String mascota, Dueno dueno, TipoCita tipo, LocalDateTime fecha) {
        List<Cita> historial = repositorio.deMascota(mascota);
        for (Requisito requisito : tipo.requisitos()) {
            requisito.incumplimiento(mascota, fecha, historial).ifPresent(motivo -> {
                throw new CitaRechazada(motivo);
            });
        }
        Cita cita = new Cita(mascota, dueno, tipo, fecha, precio(dueno, tipo, fecha));
        repositorio.guardar(cita);
        notificador.notificar(dueno, "Hola " + dueno.nombre() + ", " + mascota + " tiene " + tipo + " el " + fecha);
        return cita;
    }
}
