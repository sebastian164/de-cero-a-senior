package nosql.aplicacion;

import java.util.Optional;

/** Puerto de salida: el cerrojo de una tarea que corre en varias instancias. Cada concesión lleva una ficha y un turno creciente. */
public interface CerrojoDeTareas {

    record Concesion(String tarea, String ficha, long turno) {
    }

    Optional<Concesion> adquirir(String tarea, long vidaMs);

    boolean liberar(Concesion concesion);
}
