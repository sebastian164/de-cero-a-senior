import java.time.LocalDate;

/** Adaptador: el formato de la línea del log es un detalle del archivo, no del negocio. */
public class HistorialEnArchivo implements HistorialRenovaciones {
    private final ArchivoLog log;

    public HistorialEnArchivo(ArchivoLog log) {
        this.log = log;
    }

    @Override
    public void registrar(LocalDate fecha, String usuario, long valor, String codigoPago) {
        log.escribirLinea(fecha + ";" + usuario + ";" + valor + ";" + codigoPago);
    }
}
