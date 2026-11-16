package tienda.adaptadores.salida.memoria;

import java.util.Map;
import java.util.Optional;
import tienda.aplicacion.puertos.salida.Catalogo;
import tienda.dominio.Dinero;

public final class CatalogoEnMemoria implements Catalogo {

    private final Map<String, Dinero> precios;

    public CatalogoEnMemoria(Map<String, Dinero> precios) {
        this.precios = Map.copyOf(precios);
    }

    @Override
    public Optional<Dinero> precioDe(String producto) {
        return Optional.ofNullable(precios.get(producto));
    }
}
