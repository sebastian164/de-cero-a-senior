package bicis;

/** El alquiler no existe o ya se cerró. */
public final class AlquilerNoDisponible extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AlquilerNoDisponible(String idAlquiler) {
        super("no hay un alquiler abierto con el identificador " + idAlquiler);
    }
}
