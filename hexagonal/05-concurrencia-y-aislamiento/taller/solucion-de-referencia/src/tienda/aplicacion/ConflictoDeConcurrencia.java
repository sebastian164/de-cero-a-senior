package tienda.aplicacion;

/** Otro cambió lo mismo entre que se leyó y se intentó guardar. */
public class ConflictoDeConcurrencia extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConflictoDeConcurrencia(String recurso) {
        super(recurso + " cambió mientras se trabajaba con él");
    }
}
