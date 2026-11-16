package tienda.antes.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/** Modelo anémico: solo datos. */
@Entity
public class Pedido {
    @Id private Long id;
    @ManyToOne private Cliente cliente;
    private long total;
    private String estado = "BORRADOR";

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
