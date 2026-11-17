package precios;

import precios.Modelo.Producto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;

/**
 * Estado visible para las promociones: el carrito aplanado, lo que vale cada producto en el carrito (un producto dentro de un
 * combo vale una parte del precio del combo) y la base ya rebajada.
 */
public record Contexto(Map<Producto, Integer> unidades, Map<Producto, BigDecimal> valores, Set<String> cupones, BigDecimal base) {

    /** Precio medio por unidad de ese producto en este carrito (el de lista si solo viene suelto). */
    public BigDecimal precioUnitario(Producto p) {
        return valores.get(p).divide(BigDecimal.valueOf(unidades.get(p)), 10, RoundingMode.HALF_UP);
    }
}
