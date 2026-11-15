package banco.aplicacion.puertos.entrada;

import banco.aplicacion.VistaDeCuenta;
import banco.dominio.Movimiento;
import java.util.List;
import java.util.Optional;

public interface ConsultarCuenta {

    Optional<VistaDeCuenta> porId(String cuentaId);

    List<Movimiento> movimientosDe(String cuentaId);
}
