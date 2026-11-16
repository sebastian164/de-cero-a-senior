package banco.transferencias.aplicacion.puerto.salida;

import banco.transferencias.dominio.Cuenta;
import banco.transferencias.dominio.NumeroCuenta;

import java.util.Optional;

public interface CargarCuenta { Optional<Cuenta> porNumero(NumeroCuenta numero); }
