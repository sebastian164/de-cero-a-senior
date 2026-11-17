package tienda.pagos;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** API pública del módulo pagos. Dueño de pagos_cobros. Protege la llamada a la pasarela. */
@Service
public class Pagos {
    public static class CobroRechazado extends RuntimeException {
        public CobroRechazado(String m, Throwable causa) { super(m, causa); }
    }

    /** El tiempo vencido no dice si se cobró: el resultado es desconocido y hay que anular por clave. */
    public static class CobroIncierto extends CobroRechazado {
        public CobroIncierto(String m, Throwable causa) { super(m, causa); }
    }

    public static String clave(long pedidoId) { return "pedido-" + pedidoId; }

    private final Pasarela pasarela;
    private final JdbcClient jdbc;

    public Pagos(Pasarela pasarela, JdbcClient jdbc) { this.pasarela = pasarela; this.jdbc = jdbc; }

    /**
     * Reintento (2 intentos) por fuera y cortacircuitos por dentro; el tiempo límite de 300 ms está en la propia llamada.
     * Todos los intentos llevan la misma clave de idempotencia, así que reintentar no puede cobrar dos veces.
     */
    @Retry(name = "pasarela")
    @CircuitBreaker(name = "pasarela")
    public void cobrar(long pedidoId, BigDecimal monto) {
        CompletableFuture<Void> f = CompletableFuture.runAsync(() -> pasarela.cobrar(clave(pedidoId), monto));
        try {
            f.get(300, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            f.cancel(true);
            throw new CobroIncierto("La pasarela no respondió a tiempo", e);
        } catch (ExecutionException e) {
            throw new CobroRechazado("La pasarela falló: " + e.getCause().getMessage(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CobroRechazado("Cobro interrumpido", e);
        }
        jdbc.sql("INSERT INTO pagos_cobros (pedido_id, monto, estado) VALUES (?,?,'COBRADO') ON CONFLICT (pedido_id) DO NOTHING").params(pedidoId, monto).update();
    }

    public void reembolsar(long pedidoId) {
        int n = jdbc.sql("UPDATE pagos_cobros SET estado = 'REEMBOLSADO' WHERE pedido_id = ? AND estado = 'COBRADO'").param(pedidoId).update();
        if (n > 0) pasarela.reembolsar(clave(pedidoId));
    }

    /** Anula por clave un cobro de resultado desconocido (tiempo vencido). Idempotente. */
    public void anular(long pedidoId) { pasarela.reembolsar(clave(pedidoId)); }

    public String estado(long pedidoId) {
        return jdbc.sql("SELECT estado FROM pagos_cobros WHERE pedido_id = ?").param(pedidoId).query(String.class).optional().orElse("SIN_COBRO");
    }
}
