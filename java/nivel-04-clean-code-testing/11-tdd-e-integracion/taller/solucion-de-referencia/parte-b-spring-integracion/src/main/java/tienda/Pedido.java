package tienda;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

@Entity
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String cliente;
    @Enumerated(EnumType.STRING)
    private EstadoPedido estado;
    private long total;
    private Instant creadoEn;

    protected Pedido() {
    }

    public Pedido(String cliente, long total, Instant creadoEn) {
        this.cliente = cliente;
        this.total = total;
        this.creadoEn = creadoEn;
        this.estado = EstadoPedido.PENDIENTE;
    }

    public void entregar() {
        if (estado == EstadoPedido.ENTREGADO) {
            throw new ReglaDeNegocioException("El pedido " + id + " ya fue entregado");
        }
        estado = EstadoPedido.ENTREGADO;
    }

    public Long getId() { return id; }
    public String getCliente() { return cliente; }
    public EstadoPedido getEstado() { return estado; }
    public long getTotal() { return total; }
    public Instant getCreadoEn() { return creadoEn; }
}
