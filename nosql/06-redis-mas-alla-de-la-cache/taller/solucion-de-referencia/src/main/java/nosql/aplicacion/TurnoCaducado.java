package nosql.aplicacion;

/** Quien escribe tiene un turno más viejo que el último que escribió: su cerrojo caducó y otro dueño ya trabajó. */
public class TurnoCaducado extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TurnoCaducado(String tarea, long turno) {
        super("el turno " + turno + " de " + tarea + " está caducado: otro dueño más nuevo ya escribió");
    }
}
