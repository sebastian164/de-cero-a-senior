import java.time.Clock;
import java.time.LocalDate;

/** El negocio: depende de abstracciones (Cobros, HistorialRenovaciones, Clock), no de ACME ni de archivos. */
public class ServicioSuscripciones {

    static final long PRECIO_MENSUAL = 30_000;

    private final Cobros cobros;
    private final HistorialRenovaciones historial;
    private final Clock reloj;

    public ServicioSuscripciones(Cobros cobros, HistorialRenovaciones historial, Clock reloj) {
        this.cobros = cobros;
        this.historial = historial;
        this.reloj = reloj;
    }

    public String renovar(String usuario, int meses) {
        if (meses <= 0) throw new IllegalArgumentException("Los meses deben ser positivos");
        long valor = valorPor(meses);
        String codigo = cobros.cobrar(usuario, valor);
        LocalDate hoy = LocalDate.now(reloj);
        LocalDate vence = hoy.plusMonths(meses);
        historial.registrar(hoy, usuario, valor, codigo);
        return "Renovado hasta " + vence + " (" + codigo + ")";
    }

    static long valorPor(int meses) {
        long valor = meses * PRECIO_MENSUAL;
        return meses >= 12 ? valor * 80 / 100 : valor;
    }
}
