package mensajeria.despacho.aplicacion.puertos;

import mensajeria.despacho.dominio.Mensajero;
import mensajeria.despacho.dominio.Punto;

import java.util.List;
import java.util.Optional;

public interface Mensajeros {
    Optional<Mensajero> buscar(String id);
    List<Mensajero> cercanosA(Punto punto, double radioKm);
    /** Guarda si la versión no cambió desde que se cargó; si cambió lanza ConflictoDeVersion. */
    void guardar(Mensajero mensajero);

    class ConflictoDeVersion extends RuntimeException {
        public ConflictoDeVersion(String id) { super("Conflicto de versión en " + id); }
    }
}
