package nosql;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import nosql.aplicacion.ConsultarProducto;
import nosql.aplicacion.VistaDeProducto;

/** Una base con latencia fija que cuenta sus lecturas. */
final class BaseLenta implements ConsultarProducto {
    final Map<String, VistaDeProducto> datos = new ConcurrentHashMap<>();
    final AtomicLong lecturas = new AtomicLong();
    final long latenciaMs;

    BaseLenta(long latenciaMs) { this.latenciaMs = latenciaMs; }

    @Override
    public Optional<VistaDeProducto> porId(String id) {
        lecturas.incrementAndGet();
        if (latenciaMs > 0) try { Thread.sleep(latenciaMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return Optional.ofNullable(datos.get(id));
    }
}
