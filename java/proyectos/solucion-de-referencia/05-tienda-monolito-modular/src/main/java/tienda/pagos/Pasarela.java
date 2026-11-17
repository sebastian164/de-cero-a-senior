package tienda.pagos;

import java.math.BigDecimal;

/**
 * Puerto hacia la pasarela de pagos (externa). Toda operación lleva una clave de idempotencia:
 * repetir la misma clave nunca cobra dos veces, y anular una clave también anula un cobro que aún no terminó.
 */
public interface Pasarela {
    void cobrar(String clave, BigDecimal monto);

    /** Anula o reembolsa el cobro de esa clave. Idempotente; si el cobro todavía no llegó, queda anulado de antemano. */
    void reembolsar(String clave);
}
