package banco.cuentas;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/** Una fila de la tabla cuenta. Spring Data R2DBC la lee y la escribe; @Version da el bloqueo optimista. */
@Table("cuenta")
public record Cuenta(@Id String id, String titular, long saldoCentavos, @Version Long version) {

    public Cuenta conSaldo(long nuevoSaldo) {
        return new Cuenta(id, titular, nuevoSaldo, version);
    }
}
