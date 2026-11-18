package pedidos;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "pedidos")
public class Pedido {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private long clienteId;
    private BigDecimal total;
    private Instant creadoEn;

    protected Pedido() {
    }

    public Pedido(long clienteId, BigDecimal total) {
        this.clienteId = clienteId;
        this.total = total;
        this.creadoEn = Instant.now();
    }

    public Long getId() { return id; }
    public long getClienteId() { return clienteId; }
    public BigDecimal getTotal() { return total; }
    public Instant getCreadoEn() { return creadoEn; }
}
