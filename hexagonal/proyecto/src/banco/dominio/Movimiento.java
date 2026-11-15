package banco.dominio;

import java.time.Instant;

/** Un renglón del extracto. Una vez escrito, no cambia. */
public record Movimiento(String cuentaId, Tipo tipo, Dinero valor, Instant fecha, String referencia) {

    public enum Tipo {
        DEPOSITO,
        TRANSFERENCIA_ENVIADA,
        TRANSFERENCIA_RECIBIDA
    }
}
