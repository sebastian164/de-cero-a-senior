package banco.cuentas;

public class SaldoInsuficiente extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SaldoInsuficiente(String cuentaId) {
        super("saldo insuficiente en " + cuentaId);
    }
}
