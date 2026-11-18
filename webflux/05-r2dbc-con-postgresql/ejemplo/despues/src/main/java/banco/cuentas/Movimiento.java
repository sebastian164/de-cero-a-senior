package banco.cuentas;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("movimiento")
public record Movimiento(@Id Long id, String cuentaId, String tipo, long centavos, Instant fecha, String referencia) {
}
