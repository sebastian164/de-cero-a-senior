package banco;

import java.util.HashMap;
import java.util.Map;

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
        if (origen.equals(destino)) {
            throw new IllegalArgumentException("El origen y el destino son la misma cuenta: " + origen);
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
