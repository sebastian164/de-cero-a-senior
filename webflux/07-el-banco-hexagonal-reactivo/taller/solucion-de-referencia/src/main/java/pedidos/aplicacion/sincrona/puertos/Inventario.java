package pedidos.aplicacion.sincrona.puertos;

import java.util.Optional;
import tienda.dominio.Existencias;

public interface Inventario {
    /** Las existencias de un producto, tomando su fila para que nadie más las cambie hasta terminar la unidad de trabajo. */
    Optional<Existencias> existenciasDe(String producto);
    void guardar(Existencias existencias);
}
