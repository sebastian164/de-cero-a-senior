import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cerrada a cambios por cupones o recargos nuevos: los recibe ya ensamblados.
 * El ORDEN de aplicación queda definido aquí, en un solo lugar:
 * 1) impuesto sobre la base, 2) recargos, 3) cupón calculado sobre el desglose.
 */
public final class CalculadoraPrecio {

    private final List<Recargo> recargos;
    private final Map<String, Cupon> cupones = new LinkedHashMap<>();

    public CalculadoraPrecio(List<Recargo> recargos, List<Cupon> cupones) {
        this.recargos = List.copyOf(recargos);
        for (Cupon cupon : cupones) this.cupones.put(cupon.codigo(), cupon);
    }

    public long precioFinal(Pedido pedido) {
        long impuesto = pedido.categoria().impuestoSobre(pedido.base());
        Map<String, Long> valores = new LinkedHashMap<>();
        for (Recargo recargo : recargos) valores.merge(recargo.nombre(), recargo.valor(pedido), Long::sum);
        Desglose desglose = new Desglose(pedido, impuesto, valores);
        return desglose.subtotal() - descuento(pedido.cupon(), desglose);
    }

    private long descuento(String codigo, Desglose desglose) {
        if (codigo == null) return 0;
        Cupon cupon = cupones.get(codigo);
        if (cupon == null) throw new IllegalArgumentException("Cupón desconocido: " + codigo);
        return Math.min(cupon.descuento(desglose), desglose.subtotal());   // el precio nunca es negativo
    }
}
