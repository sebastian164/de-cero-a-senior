package migraciones;

import jakarta.persistence.Column;
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
    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;
    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private Instant creadoEn;
    @Column(nullable = false, length = 20)
    private String estado = "CREADO";

    protected Pedido() {
    }

    public Pedido(Long clienteId, BigDecimal total) {
        this.clienteId = clienteId;
        this.total = total;
    }

    public Long getId() { return id; }
    public String getEstado() { return estado; }
}
