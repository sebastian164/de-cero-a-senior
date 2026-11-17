package nosql.aplicacion;

/** El almacén falló por una causa técnica (red, tiempo de espera, disco). La aplicación no sabe de qué tecnología viene. */
public class ErrorDeAlmacenamiento extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ErrorDeAlmacenamiento(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
