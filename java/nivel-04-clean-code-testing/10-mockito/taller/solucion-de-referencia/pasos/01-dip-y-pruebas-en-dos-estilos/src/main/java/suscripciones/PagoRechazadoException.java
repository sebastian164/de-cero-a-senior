package suscripciones;

public class PagoRechazadoException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PagoRechazadoException(String mensaje) {
        super(mensaje);
    }
}
