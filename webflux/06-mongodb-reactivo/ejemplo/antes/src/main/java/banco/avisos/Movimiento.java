package banco.avisos;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Un movimiento como documento. Cómo modelar documentos se ve en el módulo nosql; aquí importa cómo enterarse de él. */
@Document("movimientos")
public record Movimiento(@Id String id, String cuentaId, long centavos, Instant fecha) {
}
