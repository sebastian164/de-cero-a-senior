package tienda.integrador.plataforma;

/** Un mensaje que no se arregla reintentando: el manejador de errores lo manda al tema de fallidos. */
public final class MensajeIlegible extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public MensajeIlegible(String motivo, Throwable causa) {
        super(motivo, causa);
    }
}
