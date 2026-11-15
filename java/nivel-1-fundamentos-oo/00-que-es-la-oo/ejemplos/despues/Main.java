public class Main {

    public static void main(String[] args) {
        Cuenta ahorros = new Cuenta("Ana", 100_000, new SinComision());
        Cuenta corriente = new Cuenta("Luis", 100_000, new ComisionPorMil(4));

        ahorros.retirar(30_000);
        corriente.retirar(30_000);

        System.out.println("Saldo de " + ahorros.getTitular() + ": " + ahorros.getSaldo());
        System.out.println("Saldo de " + corriente.getTitular() + ": " + corriente.getSaldo());

        // Esto ya no compila: el saldo es privado.
        // corriente.saldo = -500_000;

        // Y pedir más de lo que hay falla de forma explícita.
        try {
            corriente.retirar(1_000_000);
        } catch (IllegalStateException e) {
            System.out.println("Retiro rechazado: " + e.getMessage());
        }
    }
}
