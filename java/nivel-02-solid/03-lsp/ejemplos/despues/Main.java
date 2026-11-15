import java.util.List;

public class Main {

    public static void main(String[] args) {
        CuentaAhorros ahorros = new CuentaAhorros("Ahorros de Ana", 500_000);
        CuentaPlazoFijo cdt = new CuentaPlazoFijo("CDT de Ana", 2_000_000);

        cobrarCuotaDeManejo(List.of(ahorros));
        // cobrarCuotaDeManejo(List.of(cdt));  // no compila: un CDT no es Retirable

        imprimirSaldos(List.of(ahorros, cdt));
    }

    static void cobrarCuotaDeManejo(List<? extends Retirable> cuentas) {
        for (Retirable cuenta : cuentas) {
            cuenta.retirar(10_000);
        }
    }

    static void imprimirSaldos(List<? extends Cuenta> cuentas) {
        for (Cuenta cuenta : cuentas) {
            System.out.println(cuenta.nombre() + ": saldo " + cuenta.saldo());
        }
    }
}

/** Lo que TODA cuenta cumple. */
interface Cuenta {
    String nombre();
    long saldo();
}

/** Una capacidad adicional que solo algunas cuentas tienen. */
interface Retirable {
    void retirar(long monto);
}

class CuentaAhorros implements Cuenta, Retirable {

    private final String nombre;
    private long saldo;

    CuentaAhorros(String nombre, long saldo) {
        this.nombre = nombre;
        this.saldo = saldo;
    }

    @Override
    public void retirar(long monto) {
        if (monto > saldo) {
            throw new IllegalStateException("Fondos insuficientes");
        }
        saldo -= monto;
    }

    @Override public String nombre() { return nombre; }
    @Override public long saldo() { return saldo; }
}

class CuentaPlazoFijo implements Cuenta {

    private final String nombre;
    private final long saldo;

    CuentaPlazoFijo(String nombre, long saldo) {
        this.nombre = nombre;
        this.saldo = saldo;
    }

    @Override public String nombre() { return nombre; }
    @Override public long saldo() { return saldo; }
}
