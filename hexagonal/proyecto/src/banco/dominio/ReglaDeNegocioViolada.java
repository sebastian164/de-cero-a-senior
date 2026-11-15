package banco.dominio;

/** El negocio dijo que no. No es un fallo técnico: es una respuesta válida del sistema. */
public class ReglaDeNegocioViolada extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ReglaDeNegocioViolada(String mensaje) {
        super(mensaje);
    }
}
