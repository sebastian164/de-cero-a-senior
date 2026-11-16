package tienda;

import io.github.resilience4j.retry.annotation.Retry;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ClienteInventario {

    public record Existencias(long productoId, int cantidad) {}
    public record ResultadoCobro(long cobroId, long pedidoId, String estado) {}

    private final RestClient sinLimite;
    private final RestClient conLimite;

    ClienteInventario(@Qualifier("clienteSinLimite") RestClient sinLimite, @Qualifier("clienteConLimite") RestClient conLimite) {
        this.sinLimite = sinLimite;
        this.conLimite = conLimite;
    }

    public Existencias consultarSinLimite(long productoId) {
        return sinLimite.get().uri("/existencias/{id}", productoId).retrieve().body(Existencias.class);
    }

    /** Solo tiempo límite, sin reintento. */
    public Existencias consultarUnaVez(long productoId) {
        return conLimite.get().uri("/existencias/{id}", productoId).retrieve().body(Existencias.class);
    }

    @Retry(name = "inventario")                // 3 intentos, retroceso exponencial con variación
    public Existencias consultar(long productoId) {
        return consultarUnaVez(productoId);
    }

    /** Reintenta un POST no idempotente: el error del paso 3. */
    @Retry(name = "inventario")
    public ResultadoCobro cobrar(long pedidoId, BigDecimal monto) {
        return conLimite.post().uri("/cobros").body(Map.of("pedidoId", pedidoId, "monto", monto))
                .retrieve().body(ResultadoCobro.class);
    }

    /** La clave se genera una vez por operación de negocio y se repite en cada reintento. */
    @Retry(name = "inventario")
    public ResultadoCobro cobrarIdempotente(String clave, long pedidoId, BigDecimal monto) {
        return conLimite.post().uri("/cobros-idempotentes").header("Idempotency-Key", clave)
                .body(Map.of("pedidoId", pedidoId, "monto", monto)).retrieve().body(ResultadoCobro.class);
    }
}
