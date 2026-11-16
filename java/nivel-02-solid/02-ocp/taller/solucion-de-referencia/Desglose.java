import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Lo calculado antes de aplicar el cupón: los cupones deciden mirando el desglose. */
public final class Desglose {
    private final Pedido pedido;
    private final long impuesto;
    private final Map<String, Long> recargos;

    Desglose(Pedido pedido, long impuesto, Map<String, Long> recargos) {
        this.pedido = pedido;
        this.impuesto = impuesto;
        this.recargos = Collections.unmodifiableMap(new LinkedHashMap<>(recargos));
    }

    public Pedido pedido() { return pedido; }
    public long base() { return pedido.base(); }
    public long impuesto() { return impuesto; }
    public long recargo(String nombre) { return recargos.getOrDefault(nombre, 0L); }
    public long subtotal() { return base() + impuesto + recargos.values().stream().mapToLong(Long::longValue).sum(); }
}
