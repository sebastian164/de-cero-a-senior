package banco.dominio;

import java.time.LocalDate;

/** Una cuenta. Solo ella decide si puede enviar dinero. */
public final class Cuenta {

    public static final Dinero TOPE_DIARIO = Dinero.pesos(800_000);

    private final String id;
    private final String titular;
    private Dinero saldo;
    private Dinero enviadoHoy;
    private LocalDate diaDelTope;
    private final long version;                                  // para detectar cambios simultáneos (sesión 05)

    private Cuenta(String id, String titular, Dinero saldo, Dinero enviadoHoy, LocalDate diaDelTope, long version) {
        this.id = id;
        this.titular = titular;
        this.saldo = saldo;
        this.enviadoHoy = enviadoHoy;
        this.diaDelTope = diaDelTope;
        this.version = version;
    }

    public static Cuenta abrir(String id, String titular, Dinero saldoInicial, LocalDate hoy) {
        if (titular == null || titular.isBlank()) {
            throw new ReglaDeNegocioViolada("una cuenta necesita un titular");
        }
        return new Cuenta(id, titular, saldoInicial, Dinero.CERO, hoy, 0);
    }

    /** Reconstruye una cuenta que ya existía. Lo usan los adaptadores que la leen de donde esté guardada. */
    public static Cuenta existente(String id, String titular, Dinero saldo, Dinero enviadoHoy, LocalDate diaDelTope, long version) {
        return new Cuenta(id, titular, saldo, enviadoHoy, diaDelTope, version);
    }

    public void depositar(Dinero valor) {
        exigirPositivo(valor);
        saldo = saldo.mas(valor);
    }

    /** Sale dinero hacia otra cuenta. La fecha llega de fuera: la cuenta no sabe qué día es. */
    public void enviar(Dinero valor, LocalDate hoy) {
        exigirPositivo(valor);
        if (valor.esMayorQue(saldo)) {
            throw new ReglaDeNegocioViolada("saldo insuficiente en " + id);
        }
        Dinero enviado = hoy.equals(diaDelTope) ? enviadoHoy : Dinero.CERO;       // otro día: se empieza de cero
        if (enviado.mas(valor).esMayorQue(TOPE_DIARIO)) {
            throw new ReglaDeNegocioViolada("supera el tope diario de " + id);
        }
        saldo = saldo.menos(valor);
        enviadoHoy = enviado.mas(valor);
        diaDelTope = hoy;
    }

    private static void exigirPositivo(Dinero valor) {
        if (valor.esCero()) {
            throw new ReglaDeNegocioViolada("el valor debe ser mayor que cero");
        }
    }

    public String id() {
        return id;
    }

    public String titular() {
        return titular;
    }

    public Dinero saldo() {
        return saldo;
    }

    public Dinero enviadoHoy() {
        return enviadoHoy;
    }

    public LocalDate diaDelTope() {
        return diaDelTope;
    }

    public long version() {
        return version;
    }
}
