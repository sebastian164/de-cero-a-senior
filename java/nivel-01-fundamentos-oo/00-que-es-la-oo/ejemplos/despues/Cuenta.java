/**
 * La cuenta es dueña de su saldo y de las reglas que lo protegen.
 * Desde afuera no se le cambia el estado: se le pide que haga algo.
 */
public class Cuenta {

    private final String titular;
    private final PoliticaComision politicaComision;
    private long saldo;

    public Cuenta(String titular, long saldoInicial, PoliticaComision politicaComision) {
        if (titular == null || titular.isBlank()) {
            throw new IllegalArgumentException("La cuenta necesita un titular");
        }
        if (saldoInicial < 0) {
            throw new IllegalArgumentException("El saldo inicial no puede ser negativo");
        }
        if (politicaComision == null) {
            throw new IllegalArgumentException("La cuenta necesita una política de comisión");
        }
        this.titular = titular;
        this.saldo = saldoInicial;
        this.politicaComision = politicaComision;
    }

    public void depositar(long monto) {
        validarMontoPositivo(monto);
        saldo += monto;
    }

    public void retirar(long monto) {
        validarMontoPositivo(monto);
        long total = monto + politicaComision.calcular(monto);
        if (total > saldo) {
            throw new IllegalStateException("Fondos insuficientes");
        }
        saldo -= total;
    }

    public String getTitular() {
        return titular;
    }

    public long getSaldo() {
        return saldo;
    }

    private void validarMontoPositivo(long monto) {
        if (monto <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor que cero");
        }
    }
}
