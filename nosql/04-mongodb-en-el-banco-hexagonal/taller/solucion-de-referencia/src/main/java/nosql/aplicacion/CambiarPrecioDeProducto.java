package nosql.aplicacion;

import tienda.dominio.Dinero;

/** Puerto de entrada de escritura: cambiar el precio de un producto. */
public interface CambiarPrecioDeProducto {

    void cambiar(String producto, Dinero precio);
}
