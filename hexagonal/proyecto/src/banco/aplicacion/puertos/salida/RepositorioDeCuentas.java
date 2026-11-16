package banco.aplicacion.puertos.salida;

import banco.dominio.Cuenta;
import java.util.Optional;

public interface RepositorioDeCuentas {

    String siguienteId();

    /** Lanza ConflictoDeConcurrencia si otro cambió la cuenta desde que se leyó. */
    void guardar(Cuenta cuenta);

    Optional<Cuenta> buscar(String id);
}
