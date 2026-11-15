/**
 * Funciona. Ese es justamente el problema: que funcione no dice nada
 * sobre lo que cuesta cambiarlo.
 */
public class Main {

    public static void main(String[] args) {
        Cuenta cuenta = new Cuenta();
        cuenta.titular = "Ana";
        cuenta.tipo = "CORRIENTE";
        cuenta.saldo = 100_000;

        retirar(cuenta, 30_000);
        System.out.println("Saldo de " + cuenta.titular + ": " + cuenta.saldo);

        // Nada impide esto. La regla "el saldo nunca es negativo"
        // vive en retirar(), no en la cuenta.
        cuenta.saldo = -500_000;
        System.out.println("Saldo de " + cuenta.titular + ": " + cuenta.saldo);
    }

    static void retirar(Cuenta cuenta, long monto) {
        long comision;
        if (cuenta.tipo.equals("AHORROS")) {
            comision = 0;
        } else if (cuenta.tipo.equals("CORRIENTE")) {
            comision = monto * 4 / 1000;
        } else {
            throw new IllegalArgumentException("Tipo de cuenta desconocido: " + cuenta.tipo);
        }

        if (cuenta.saldo >= monto + comision) {
            cuenta.saldo -= monto + comision;
        } else {
            System.out.println("Fondos insuficientes");
        }
    }
}
