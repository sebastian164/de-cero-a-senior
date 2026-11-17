package nosql.aplicacion;

import java.util.Optional;
import tienda.aplicacion.puertos.salida.Catalogo;
import tienda.aplicacion.puertos.salida.Inventario;

/** El caso de uso de consulta. Lee solo puertos de salida y devuelve una vista, nunca las entidades con las que el dominio decide. */
public final class ConsultaDeProductos implements ConsultarProducto {

    private final Catalogo catalogo;
    private final Inventario inventario;

    public ConsultaDeProductos(Catalogo catalogo, Inventario inventario) {
        this.catalogo = catalogo;
        this.inventario = inventario;
    }

    @Override
    public Optional<VistaDeProducto> porId(String producto) {
        return catalogo.precioDe(producto).map(precio -> new VistaDeProducto(producto, precio.centavos(),
                inventario.existenciasDe(producto).map(e -> e.unidades()).orElse(0)));
    }
}
