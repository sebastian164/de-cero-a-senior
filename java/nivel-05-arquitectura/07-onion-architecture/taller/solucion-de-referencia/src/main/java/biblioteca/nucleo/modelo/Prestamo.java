package biblioteca.nucleo.modelo;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class Prestamo {
    public static final long MULTA_POR_DIA = 1_000;

    private final String socioId;
    private final String ejemplar;
    private final LocalDate inicio;
    private final LocalDate limite;
    private LocalDate devuelto;

    public Prestamo(String socioId, String ejemplar, LocalDate inicio, LocalDate limite) {
        this.socioId = socioId;
        this.ejemplar = ejemplar;
        this.inicio = inicio;
        this.limite = limite;
    }

    /** Devuelve la multa causada (0 si llega a tiempo). */
    public long devolver(LocalDate fecha) {
        if (devuelto != null) throw new IllegalStateException("ya devuelto");
        devuelto = fecha;
        long diasTarde = Math.max(0, ChronoUnit.DAYS.between(limite, fecha));
        return diasTarde * MULTA_POR_DIA;
    }

    public boolean activo() { return devuelto == null; }
    public String socioId() { return socioId; }
    public String ejemplar() { return ejemplar; }
    public LocalDate inicio() { return inicio; }
    public LocalDate limite() { return limite; }
}
