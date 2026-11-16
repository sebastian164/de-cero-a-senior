package taller.builder;

import java.math.BigDecimal;
import java.util.Optional;

public class Main {

    public static void main(String[] args) {
        // Paso 1: constructor telescópico. ¿Qué es cada null? ¿Qué es cada String?
        SolicitudCreditoTelescopica t = new SolicitudCreditoTelescopica(
                "Ana", 80_000_000L, 72, new BigDecimal("2.1"), "Luis", "Seguro Vida", null);
        System.out.println("Telescópica: " + t);

        // Paso 2: Builder.
        SolicitudCredito s = SolicitudCredito.builder("Ana", 80_000_000L)
                .plazoMeses(72)
                .codeudor("Luis")
                .seguro("Seguro Vida")
                .build();
        System.out.println("Builder:     " + s);

        System.out.println("Por defecto: " + SolicitudCredito.builder("Pedro", 5_000_000L).build());

        try {
            SolicitudCredito.builder("Marta", 60_000_000L).build();
        } catch (IllegalStateException e) {
            System.out.println("Rechazada: " + e.getMessage());
        }
    }
}

/** Paso 1: la versión mala, para comparar. */
final class SolicitudCreditoTelescopica {
    final String cliente; final long monto; final int plazo; final BigDecimal tasa;
    final String codeudor; final String seguro; final String cuenta;

    SolicitudCreditoTelescopica(String cliente, long monto) { this(cliente, monto, 12); }
    SolicitudCreditoTelescopica(String cliente, long monto, int plazo) {
        this(cliente, monto, plazo, SolicitudCredito.tasaPorDefecto(plazo));
    }
    SolicitudCreditoTelescopica(String cliente, long monto, int plazo, BigDecimal tasa) {
        this(cliente, monto, plazo, tasa, null, null, null);
    }
    SolicitudCreditoTelescopica(String cliente, long monto, int plazo, BigDecimal tasa,
                                String codeudor, String seguro, String cuenta) {
        this.cliente = cliente; this.monto = monto; this.plazo = plazo; this.tasa = tasa;
        this.codeudor = codeudor; this.seguro = seguro; this.cuenta = cuenta;
    }

    @Override public String toString() {
        return cliente + " $" + monto + " a " + plazo + "m tasa " + tasa + " codeudor=" + codeudor
                + " seguro=" + seguro + " cuenta=" + cuenta;
    }
}

/** Paso 2: objeto inmutable construido con Builder escrito a mano. */
final class SolicitudCredito {

    static final long TOPE_SIN_CODEUDOR = 50_000_000L;
    static final int PLAZO_MAX_SIN_SEGURO = 60;

    private final String cliente;
    private final long monto;
    private final int plazoMeses;
    private final BigDecimal tasaMensual;
    private final String codeudor;
    private final String seguro;
    private final String cuentaDesembolso;

    private SolicitudCredito(Builder b) {
        this.cliente = b.cliente;
        this.monto = b.monto;
        this.plazoMeses = b.plazoMeses;
        this.tasaMensual = b.tasaMensual != null ? b.tasaMensual : tasaPorDefecto(b.plazoMeses);
        this.codeudor = b.codeudor;
        this.seguro = b.seguro;
        this.cuentaDesembolso = b.cuentaDesembolso;
    }

    /** Tasa mensual por defecto según el plazo. */
    static BigDecimal tasaPorDefecto(int plazo) {
        if (plazo <= 24) return new BigDecimal("1.5");
        if (plazo <= 60) return new BigDecimal("1.8");
        return new BigDecimal("2.1");
    }

    /** Obligatorios al inicio: el compilador impide olvidarlos. */
    static Builder builder(String cliente, long monto) {
        return new Builder(cliente, monto);
    }

    String cliente() { return cliente; }
    long monto() { return monto; }
    int plazoMeses() { return plazoMeses; }
    BigDecimal tasaMensual() { return tasaMensual; }
    Optional<String> codeudor() { return Optional.ofNullable(codeudor); }
    Optional<String> seguro() { return Optional.ofNullable(seguro); }
    Optional<String> cuentaDesembolso() { return Optional.ofNullable(cuentaDesembolso); }

    @Override public String toString() {
        return cliente + " $" + monto + " a " + plazoMeses + " meses, tasa " + tasaMensual + "% m.v."
                + codeudor().map(c -> ", codeudor " + c).orElse("")
                + seguro().map(c -> ", seguro " + c).orElse("")
                + cuentaDesembolso().map(c -> ", desembolso a " + c).orElse("");
    }

    static final class Builder {
        private final String cliente;
        private final long monto;
        private int plazoMeses = 12;
        private BigDecimal tasaMensual;
        private String codeudor;
        private String seguro;
        private String cuentaDesembolso;

        private Builder(String cliente, long monto) {
            this.cliente = cliente;
            this.monto = monto;
        }

        Builder plazoMeses(int plazo) { this.plazoMeses = plazo; return this; }
        Builder tasaMensual(BigDecimal tasa) { this.tasaMensual = tasa; return this; }
        Builder codeudor(String codeudor) { this.codeudor = codeudor; return this; }
        Builder seguro(String seguro) { this.seguro = seguro; return this; }
        Builder cuentaDesembolso(String cuenta) { this.cuentaDesembolso = cuenta; return this; }

        /** Las reglas que cruzan campos se validan aquí: el orden de las llamadas no importa. */
        SolicitudCredito build() {
            if (cliente == null || cliente.isBlank()) throw new IllegalStateException("El cliente es obligatorio");
            if (monto <= 0) throw new IllegalStateException("El monto debe ser mayor que cero");
            if (plazoMeses < 6 || plazoMeses > 84) throw new IllegalStateException("El plazo debe estar entre 6 y 84 meses");
            if (monto > TOPE_SIN_CODEUDOR && codeudor == null)
                throw new IllegalStateException("Montos superiores a 50 millones requieren codeudor");
            if (plazoMeses > PLAZO_MAX_SIN_SEGURO && seguro == null)
                throw new IllegalStateException("Plazos superiores a 60 meses requieren seguro");
            return new SolicitudCredito(this);
        }
    }
}
