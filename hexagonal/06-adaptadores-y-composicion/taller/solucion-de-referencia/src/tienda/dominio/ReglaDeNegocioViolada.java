package tienda.dominio;

/** El negocio dijo que no. Es una respuesta válida, no un fallo técnico. */
public class ReglaDeNegocioViolada extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ReglaDeNegocioViolada(String mensaje) {
        super(mensaje);
    }
}
