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

    public Cita registrar(String mascota, Dueno dueno, TipoCita tipo, LocalDateTime fecha) {
        List<Cita> historial = repositorio.deMascota(mascota);
        for (Requisito requisito : tipo.requisitos()) {
            requisito.incumplimiento(mascota, fecha, historial).ifPresent(motivo -> {
                throw new CitaRechazada(motivo);
            });
        }
        Cita cita = new Cita(mascota, dueno, tipo, fecha, tipo.precio());
        repositorio.guardar(cita);
        notificador.notificar(dueno, "Hola " + dueno.nombre() + ", " + mascota + " tiene " + tipo + " el " + fecha);
        return cita;
    }
}
