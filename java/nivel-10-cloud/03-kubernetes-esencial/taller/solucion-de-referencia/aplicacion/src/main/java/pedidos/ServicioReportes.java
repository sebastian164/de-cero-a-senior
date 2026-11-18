package pedidos;

import java.math.BigDecimal;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/** Consulta costosa: suma los pedidos de un cliente. Con spring.cache.type=none no hay caché. */
@Service
public class ServicioReportes {
    private final RepositorioPedidos pedidos;

    public ServicioReportes(RepositorioPedidos pedidos) {
        this.pedidos = pedidos;
    }

    @Cacheable("reporte-cliente")
    public Reporte delCliente(long clienteId) {
        return new Reporte(clienteId, pedidos.countByClienteId(clienteId), pedidos.totalDelCliente(clienteId));
    }

    public record Reporte(long clienteId, long pedidos, BigDecimal total) {
    }
}
