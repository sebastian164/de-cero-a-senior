package banco.transferencias.dominio;

public class Cuenta {
    private final NumeroCuenta numero;
    private final Dinero topeDiario;
    private Dinero saldo;

    public Cuenta(NumeroCuenta numero, Dinero saldo, Dinero topeDiario) {
        this.numero = numero;
        this.saldo = saldo;
        this.topeDiario = topeDiario;
    }

    /** Saldo suficiente y tope diario: reglas de la cuenta de origen. */
    public void debitar(Dinero monto, Dinero transferidoHoy) {
        if (saldo.compareTo(monto) < 0) throw new TransferenciaRechazada("Saldo insuficiente");
        if (transferidoHoy.mas(monto).compareTo(topeDiario) > 0) throw new TransferenciaRechazada("Supera el tope diario");
        saldo = saldo.menos(monto);
    }

    public void acreditar(Dinero monto) { saldo = saldo.mas(monto); }

    public NumeroCuenta numero() { return numero; }
    public Dinero saldo() { return saldo; }
    public Dinero topeDiario() { return topeDiario; }
}
