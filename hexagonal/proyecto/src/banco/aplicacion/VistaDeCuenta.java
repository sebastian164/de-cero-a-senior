package banco.aplicacion;

import banco.dominio.Cuenta;

/** Lo que el exterior ve de una cuenta. No es la cuenta: no tiene comportamiento. */
public record VistaDeCuenta(String id, String titular, long saldoCentavos) {

    public static VistaDeCuenta de(Cuenta cuenta) {
        return new VistaDeCuenta(cuenta.id(), cuenta.titular(), cuenta.saldo().centavos());
    }
}
