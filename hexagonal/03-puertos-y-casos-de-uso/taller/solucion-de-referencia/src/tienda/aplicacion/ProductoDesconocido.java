package tienda.aplicacion;

import tienda.dominio.ReglaDeNegocioViolada;

/** El catálogo no tiene ese producto. Para quien llama es un rechazo del negocio, como en el código original (422). */
public class ProductoDesconocido extends ReglaDeNegocioViolada {

    private static final long serialVersionUID = 1L;

    public ProductoDesconocido(String producto) {
        super("producto desconocido: " + producto);
    }
}
