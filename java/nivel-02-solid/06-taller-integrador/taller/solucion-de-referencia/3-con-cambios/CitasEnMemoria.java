import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Doble en memoria para verificar reglas. */
public final class CitasEnMemoria implements RepositorioCitas {
    private final List<Cita> citas = new ArrayList<>();
    @Override public void guardar(Cita cita) { citas.add(cita); }
    @Override public List<Cita> deMascota(String m) { return citas.stream().filter(c -> c.mascota().equals(m)).toList(); }
    @Override public List<Cita> deDuenoEnAnio(String correo, int anio) {
        return citas.stream().filter(c -> c.dueno().correo().equals(correo) && c.fecha().getYear() == anio).toList();
    }
    @Override public List<Cita> delDia(LocalDate d) { return citas.stream().filter(c -> c.fecha().toLocalDate().equals(d)).toList(); }
}
