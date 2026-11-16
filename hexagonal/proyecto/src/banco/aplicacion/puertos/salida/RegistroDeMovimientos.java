package banco.aplicacion.puertos.salida;

import banco.dominio.Movimiento;
import java.util.List;

public interface RegistroDeMovimientos {

    void registrar(Movimiento movimiento);

    List<Movimiento> de(String cuentaId);
}
