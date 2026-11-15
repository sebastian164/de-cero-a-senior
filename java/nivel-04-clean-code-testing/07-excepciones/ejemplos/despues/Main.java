import java.util.HashMap;
import java.util.Map;

public class Main {

    public static void main(String[] args) {
        Cuentas cuentas = new Cuentas();
        intentar(() -> cuentas.transferir("A", "B", 500_000));
        intentar(() -> cuentas.transferir("A", "X", 10));
        intentar(() -> cuentas.transferir("A", "B", 40_000));
        System.out.println(cuentas.saldos());
    }

    /** La frontera: aquí sí se sabe qué hacer con el error (informar). */
    static void intentar(Runnable operacion) {
        try {
            operacion.run();
            System.out.println("Transferencia realizada");
        } catch (ReglaDeNegocioException e) {
            System.out.println("Rechazada: " + e.getMessage());
        }
    }
}

/** Raíz de los errores de negocio: permite tratarlos como familia. */
class ReglaDeNegocioException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    ReglaDeNegocioException(String mensaje) { super(mensaje); }
}

class CuentaNoEncontradaException extends ReglaDeNegocioException {
    private static final long serialVersionUID = 1L;
    CuentaNoEncontradaException(String cuenta) { super("No existe la cuenta " + cuenta); }
}

class FondosInsuficientesException extends ReglaDeNegocioException {
    private static final long serialVersionUID = 1L;
    FondosInsuficientesException(String cuenta, long saldo, long monto) {
        super("Fondos insuficientes en " + cuenta + ": saldo " + saldo + ", solicitado " + monto);
    }
}

class Cuentas {

    private final Map<String, Long> saldos = new HashMap<>(Map.of("A", 100_000L, "B", 0L));

    void transferir(String origen, String destino, long monto) {
        if (monto <= 0) {
            throw new IllegalArgumentException("El monto debe ser positivo: " + monto);
        }
        // Validar todo antes de modificar nada: o se hace completo o no se hace.
        long saldoOrigen = saldoDe(origen);
        long saldoDestino = saldoDe(destino);
        if (saldoOrigen < monto) {
            throw new FondosInsuficientesException(origen, saldoOrigen, monto);
        }
        saldos.put(origen, saldoOrigen - monto);
        saldos.put(destino, saldoDestino + monto);
    }

    private long saldoDe(String cuenta) {
        Long saldo = saldos.get(cuenta);
        if (saldo == null) {
            throw new CuentaNoEncontradaException(cuenta);
        }
        return saldo;
    }

    Map<String, Long> saldos() {
        return Map.copyOf(saldos);
    }
}
