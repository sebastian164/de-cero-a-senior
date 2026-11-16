package biblioteca.nucleo.modelo;

public class PrestamoRechazado extends RuntimeException {
    public PrestamoRechazado(String motivo) { super(motivo); }
}
