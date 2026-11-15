package vigilancia.dominio;

import java.time.LocalDate;

/** Lo que una cuenta ha enviado en un día. Decide cuándo hay que avisar. */
public final class EnviosDelDia {

    public static final long UMBRAL_CENTAVOS = 50_000_000;       // 500.000 pesos

    private final String cuentaId;
    private final LocalDate dia;
    private long totalCentavos;

    public EnviosDelDia(String cuentaId, LocalDate dia, long totalCentavos) {
        this.cuentaId = cuentaId;
        this.dia = dia;
        this.totalCentavos = totalCentavos;
    }

    /** Suma un envío. Devuelve verdadero solo la vez que el total cruza el umbral. */
    public boolean registrar(long centavos) {
        boolean estabaDebajo = totalCentavos <= UMBRAL_CENTAVOS;
        totalCentavos += centavos;
        return estabaDebajo && totalCentavos > UMBRAL_CENTAVOS;
    }

    public String cuentaId() {
        return cuentaId;
    }

    public LocalDate dia() {
        return dia;
    }

    public long totalCentavos() {
        return totalCentavos;
    }
}
