import java.util.List;
import java.util.Optional;

/** El catálogo de tipos de cita: precio y requisitos de cada uno, en un solo lugar. */
public final class TiposDeCita {
    private TiposDeCita() { }

    public static final TipoCita CONSULTA = new TipoCita("CONSULTA", 60_000, List.of());
    public static final TipoCita VACUNACION = new TipoCita("VACUNACION", 45_000, List.of(new RequisitoIntervaloVacunas("VACUNACION", 30)));
    public static final TipoCita CIRUGIA = new TipoCita("CIRUGIA", 400_000, List.of(new RequisitoConsultaPrevia()));
    public static final TipoCita PELUQUERIA = new TipoCita("PELUQUERIA", 35_000, List.of());

    public static final List<TipoCita> TODOS = List.of(CONSULTA, VACUNACION, CIRUGIA, PELUQUERIA);

    public static Optional<TipoCita> porNombre(String nombre) {
        return TODOS.stream().filter(t -> t.nombre().equals(nombre)).findFirst();
    }
}
