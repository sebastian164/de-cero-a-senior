package indices;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "pedidos")
public class Pedido {
    @Id
    private Long id;
    private Long clienteId;
    private String correo;
    private String estado;
    private String canal;
    private BigDecimal total;
    private Instant creadoEn;

    protected Pedido() {
    }

    public Long getId() { return id; }
    public String getCorreo() { return correo; }
}
