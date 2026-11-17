package nosql.adaptadores.redis;

import nosql.aplicacion.CambiarPrecioDeProducto;
import tienda.dominio.Dinero;

/** Decorador del puerto de entrada: cambia el precio y, después de confirmar, borra la clave de la caché. */
public final class CambiarPrecioInvalidando implements CambiarPrecioDeProducto {
    private final CambiarPrecioDeProducto siguiente;
    private final CatalogoConCache cache;

    public CambiarPrecioInvalidando(CambiarPrecioDeProducto siguiente, CatalogoConCache cache) { this.siguiente = siguiente; this.cache = cache; }

    @Override
    public void cambiar(String producto, Dinero precio) {
        siguiente.cambiar(producto, precio);          // la transacción ya está confirmada al volver
        cache.invalidar(producto);                    // si se borrara antes, otra lectura podría volver a guardar el valor viejo
    }
}
