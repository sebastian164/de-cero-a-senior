package banco.web;

/** El servicio de riesgo no respondió a tiempo o respondió mal. No es culpa de quien llama. */
public class RiesgoNoDisponible extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RiesgoNoDisponible(Throwable causa) {
        super("el servicio de riesgo no está disponible", causa);
    }
}
