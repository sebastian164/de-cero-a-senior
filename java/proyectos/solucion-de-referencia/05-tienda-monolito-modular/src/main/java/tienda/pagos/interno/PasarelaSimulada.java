package tienda.pagos.interno;

import org.springframework.stereotype.Component;
import tienda.pagos.Pasarela;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * La pasarela de mentira: se puede configurar para fallar o tardar, y cuenta las llamadas.
 * Se comporta como una pasarela remota: en modo LENTA el cobro SÍ termina a los 2 s aunque el cliente ya no espere,
 * deduplica por clave de idempotencia y respeta una anulación recibida antes de que el cobro termine.
 */
@Component
public class PasarelaSimulada implements Pasarela {
    public enum Modo { OK, FALLA, LENTA }

    public volatile Modo modo = Modo.OK;
    public final AtomicInteger llamadas = new AtomicInteger();
    public final AtomicInteger reembolsos = new AtomicInteger();
    /** Cobros que se aplicaron de verdad, por clave (como mucho uno por clave). */
    private final Map<String, BigDecimal> cobrados = new ConcurrentHashMap<>();
    private final Set<String> anuladas = ConcurrentHashMap.newKeySet();
    private final Set<String> reembolsadas = ConcurrentHashMap.newKeySet();
    /** Cuántas veces se aplicó un cobro por clave: la deduplicación debe mantenerlo en 1. */
    private final Map<String, AtomicInteger> aplicaciones = new ConcurrentHashMap<>();

    @Override
    public void cobrar(String clave, BigDecimal monto) {
        llamadas.incrementAndGet();
        switch (modo) {
            case FALLA -> throw new IllegalStateException("La pasarela respondió 503");
            case LENTA -> dormirSinInterrupcion(2_000);          // el remoto sigue trabajando aunque el cliente se rinda
            case OK -> { }
        }
        if (anuladas.contains(clave)) return;                    // la anulación llegó primero: no se cobra
        if (cobrados.putIfAbsent(clave, monto) == null) aplicaciones.computeIfAbsent(clave, k -> new AtomicInteger()).incrementAndGet();
    }

    @Override
    public void reembolsar(String clave) {
        anuladas.add(clave);
        if (cobrados.containsKey(clave) && reembolsadas.add(clave)) reembolsos.incrementAndGet();
    }

    /** Cobros aplicados de verdad para esa clave (0 o 1). */
    public int cobrosEfectivos(String clave) { return aplicaciones.getOrDefault(clave, new AtomicInteger()).get(); }

    /** Lo que el cliente paga al final: lo cobrado menos lo reembolsado o anulado. */
    public BigDecimal cobroNeto(String clave) {
        return cobrados.containsKey(clave) && !anuladas.contains(clave) ? cobrados.get(clave) : BigDecimal.ZERO;
    }

    private static void dormirSinInterrupcion(long ms) {
        long fin = System.nanoTime() + ms * 1_000_000;
        while (true) {
            long resto = (fin - System.nanoTime()) / 1_000_000;
            if (resto <= 0) return;
            try { Thread.sleep(resto); } catch (InterruptedException ignorada) { /* un servicio remoto no se entera */ }
        }
    }
}
