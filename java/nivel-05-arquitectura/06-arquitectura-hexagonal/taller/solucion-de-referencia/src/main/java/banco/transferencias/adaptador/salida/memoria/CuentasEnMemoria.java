package banco.transferencias.adaptador.salida.memoria;

import banco.transferencias.aplicacion.puerto.salida.CargarCuenta;
import banco.transferencias.aplicacion.puerto.salida.GuardarCuenta;
import banco.transferencias.dominio.Cuenta;
import banco.transferencias.dominio.NumeroCuenta;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class CuentasEnMemoria implements CargarCuenta, GuardarCuenta {
    private final Map<NumeroCuenta, Cuenta> cuentas = new ConcurrentHashMap<>();

    @Override public Optional<Cuenta> porNumero(NumeroCuenta numero) { return Optional.ofNullable(cuentas.get(numero)); }
    @Override public void guardar(Cuenta cuenta) { cuentas.put(cuenta.numero(), cuenta); }
}
