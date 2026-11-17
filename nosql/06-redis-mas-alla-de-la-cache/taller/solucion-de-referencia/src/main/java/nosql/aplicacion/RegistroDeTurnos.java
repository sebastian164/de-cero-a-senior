package nosql.aplicacion;

/**
 * Puerto de salida: los turnos de las tareas. Los emite y los comprueba el mismo recurso que se protege (la base de los pedidos),
 * no el almacén del cerrojo: así sobreviven a que el cerrojo se pierda.
 */
public interface RegistroDeTurnos {

    /** El siguiente turno de la tarea; siempre mayor que cualquiera emitido antes. */
    long siguiente(String tarea);

    /** Dentro de la unidad de trabajo en curso: anota el turno si no es más viejo que el último que escribió. @throws TurnoCaducado */
    void exigir(String tarea, long turno);
}
