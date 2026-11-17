package tienda.adaptadores.salida.memoria;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import nosql.aplicacion.AdministrarCatalogo;
import tienda.aplicacion.ProductoDesconocido;
import tienda.aplicacion.puertos.salida.Catalogo;
import tienda.dominio.Dinero;

/** El catálogo en memoria que se puede escribir (el del núcleo es inmutable). */
public final class CatalogoMutable implements Catalogo, AdministrarCatalogo {

    private final Map<String, Dinero> precios = new ConcurrentHashMap<>();

    @Override
    public Optional<Dinero> precioDe(String producto) {
        return Optional.ofNullable(precios.get(producto));
    }

    @Override
    public void definir(String producto, String nombre, Dinero precio) {
        precios.put(producto, precio);
    }

    @Override
    public void cambiarPrecio(String producto, Dinero precio) {
        if (precios.replace(producto, precio) == null) {
            throw new ProductoDesconocido(producto);
        }
    }
}
