import java.time.LocalDate;
import java.util.List;

/** Caso de uso: el reporte del día. Devuelve texto; quién lo imprime es asunto de Main. */
public final class ReporteDiario {

    private final RepositorioCitas repositorio;

    public ReporteDiario(RepositorioCitas repositorio) {
        this.repositorio = repositorio;
    }

    public String generar(LocalDate dia) {
        List<Cita> citas = repositorio.delDia(dia);
        StringBuilder texto = new StringBuilder("Reporte del " + dia + "\n");
        long total = 0;
        for (Cita c : citas) {
            texto.append(c.fecha()).append(' ').append(c.mascota()).append(" (").append(c.dueno().nombre()).append(") ")
                    .append(c.tipo()).append(' ').append(c.precio()).append('\n');
            total += c.precio();
        }
        return texto.append("Total facturado: ").append(total).toString();
    }
}
