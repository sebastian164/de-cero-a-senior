import java.time.LocalDate;

/** Puerto: dejar constancia de una renovación. No sabe de archivos. */
public interface HistorialRenovaciones {
    void registrar(LocalDate fecha, String usuario, long valor, String codigoPago);
}
