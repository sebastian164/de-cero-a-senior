package precios;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Catálogo: productos sueltos y combos (un combo puede contener combos). */
public final class Modelo {
    private Modelo() {}

    public static BigDecimal d(String v) { return new BigDecimal(v); }

    public static BigDecimal redondear(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }

    public sealed interface Item permits Producto, Combo {
        String nombre();
        /** Productos sueltos que componen el item, con su cantidad. */
        void aplanar(int cantidad, Map<Producto, Integer> destino);
        /** Suma de los precios de lista de los productos sueltos que lo componen (el precio sin la oferta del combo). */
        BigDecimal lista();
        /**
         * Reparte {@code precio} (lo que cuesta este item en el carrito, por unidad) entre sus productos sueltos en proporción
         * a su precio de lista, y acumula en {@code valores} lo que vale cada producto en el carrito.
         */
        void repartir(BigDecimal precio, int cantidad, Map<Producto, BigDecimal> valores);
    }

    public record Producto(String nombre, BigDecimal precio, String categoria) implements Item {
        public void aplanar(int cantidad, Map<Producto, Integer> destino) { destino.merge(this, cantidad, Integer::sum); }
        public BigDecimal lista() { return precio; }
        public void repartir(BigDecimal asignado, int cantidad, Map<Producto, BigDecimal> valores) {
            valores.merge(this, asignado.multiply(BigDecimal.valueOf(cantidad)), BigDecimal::add);
        }
    }

    /** El combo tiene precio propio: se reparte entre sus productos en proporción a su precio de lista. */
    public record Combo(String nombre, BigDecimal precio, List<Item> contenido) implements Item {
        public void aplanar(int cantidad, Map<Producto, Integer> destino) { contenido.forEach(i -> i.aplanar(cantidad, destino)); }
        public BigDecimal lista() { return contenido.stream().map(Item::lista).reduce(BigDecimal.ZERO, BigDecimal::add); }
        public void repartir(BigDecimal asignado, int cantidad, Map<Producto, BigDecimal> valores) {
            BigDecimal total = lista();
            for (Item i : contenido) {                                   // también para combos anidados: recursivo
                BigDecimal parte = asignado.multiply(i.lista()).divide(total, 10, RoundingMode.HALF_UP);
                i.repartir(parte, cantidad, valores);
            }
        }
    }

    public record Linea(Item item, int cantidad) {}

    public record Carrito(List<Linea> lineas, Set<String> cupones) {
        public Carrito(List<Linea> lineas) { this(lineas, Set.of()); }
    }

    public record Descuento(String promocion, BigDecimal monto, String detalle) {}

    public record Resultado(BigDecimal subtotal, List<Descuento> descuentos, BigDecimal envio, BigDecimal impuestos, BigDecimal total) {
        public String explicacion() {
            StringBuilder sb = new StringBuilder("subtotal " + subtotal);
            descuentos.forEach(x -> sb.append("\n- ").append(x.promocion()).append(": -").append(x.monto()).append(" (").append(x.detalle()).append(")"));
            sb.append("\nenvío ").append(envio).append("\nimpuestos ").append(impuestos).append("\ntotal ").append(total);
            return sb.toString();
        }
    }
}
