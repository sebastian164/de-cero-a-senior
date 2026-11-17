package inventario.aplicacion;

/** El mensaje no se puede leer o no cumple el contrato. Reintentarlo no lo arregla. */
public class EventoInvalido extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public EventoInvalido(String motivo, Throwable causa) {
        super(motivo, causa);
    }

    public EventoInvalido(String motivo) {
        super(motivo);
    }
}
