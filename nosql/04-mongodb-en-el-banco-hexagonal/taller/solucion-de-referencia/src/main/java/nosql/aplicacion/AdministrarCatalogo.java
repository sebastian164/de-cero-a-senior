package nosql.aplicacion;

import tienda.dominio.Dinero;

/** Puerto de salida: escribir el catálogo. El puerto {@code Catalogo} del núcleo solo lo lee. */
public interface AdministrarCatalogo {

    void definir(String producto, String nombre, Dinero precio);

    /** @throws tienda.aplicacion.ProductoDesconocido si el producto no existe */
    void cambiarPrecio(String producto, Dinero precio);
}
