package banco.aplicacion.puertos.entrada;

import banco.aplicacion.VistaDeCuenta;

public interface Depositar {

    VistaDeCuenta depositar(String cuentaId, long centavos);
}
