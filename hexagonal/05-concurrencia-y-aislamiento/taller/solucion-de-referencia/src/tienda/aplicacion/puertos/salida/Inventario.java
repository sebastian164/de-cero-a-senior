package tienda.aplicacion.puertos.salida;

import java.util.Optional;
import tienda.dominio.Existencias;

/** Las existencias de cada producto. Se leen y se escriben junto con los pedidos. */
public interface Inventario {

    Optional<Existencias> existenciasDe(String producto);

    void guardar(Existencias existencias);
}
