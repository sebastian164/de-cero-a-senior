package banco.aplicacion.puertos.entrada;

import banco.aplicacion.VistaDeCuenta;

public interface AbrirCuenta {

    VistaDeCuenta abrir(String titular, long saldoInicialCentavos);
}
