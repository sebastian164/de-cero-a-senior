import java.time.LocalDate;
import java.util.List;

/** Puerto de persistencia, definido por lo que el negocio necesita. */
public interface RepositorioCitas {
    void guardar(Cita cita);
    List<Cita> deMascota(String mascota);
    List<Cita> delDia(LocalDate dia);
    List<Cita> deDuenoEnAnio(String correo, int anio);
}
