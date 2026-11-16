package colecciones;

import java.time.LocalDate;
import java.util.Comparator;

public record Pedido(String cliente, Estado estado, LocalDate fecha) {
    public enum Estado { PENDIENTE, PAGADO, ENVIADO }

    /** Estado (orden del enum), luego fecha descendente, luego cliente. */
    public static final Comparator<Pedido> ORDEN_DE_TRABAJO = Comparator
            .comparing(Pedido::estado)
            .thenComparing(Pedido::fecha, Comparator.reverseOrder())
            .thenComparing(Pedido::cliente);
}
