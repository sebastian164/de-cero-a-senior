import java.util.List;

public class Main {

    public static void main(String[] args) {
        List<Cuenta> cuentas = List.of(
                new Cuenta("Ahorros de Ana", 500_000),
                new CuentaPlazoFijo("CDT de Ana", 2_000_000));

        cobrarCuotaDeManejo(cuentas);
    }

    /** Escrito contra Cuenta. Debería funcionar con cualquier Cuenta. */
    static void cobrarCuotaDeManejo(List<Cuenta> cuentas) {
        for (Cuenta cuenta : cuentas) {
            try {
                cuenta.retirar(10_000);
                System.out.println(cuenta.nombre() + ": saldo " + cuenta.saldo());
            } catch (UnsupportedOperationException e) {
                System.out.println(cuenta.nombre() + ": FALLO - " + e.getMessage());
            }
        }
    }
}

class Cuenta {

    private final String nombre;
    protected long saldo;

    Cuenta(String nombre, long saldo) {
        this.nombre = nombre;
        this.saldo = saldo;
    }

    void retirar(long monto) {
        if (monto > saldo) {
            throw new IllegalStateException("Fondos insuficientes");
        }
        saldo -= monto;
    }

    String nombre() { return nombre; }
    long saldo() { return saldo; }
}

/** "Un CDT es una cuenta", así que hereda. Pero de un CDT no se retira. */
class CuentaPlazoFijo extends Cuenta {

    CuentaPlazoFijo(String nombre, long saldo) {
        super(nombre, saldo);
    }

    @Override
    void retirar(long monto) {
        throw new UnsupportedOperationException("No se puede retirar de un plazo fijo");
    }
}
