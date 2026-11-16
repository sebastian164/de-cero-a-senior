package tienda.despues.pedidos;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import tienda.despues.clientes.Cliente;

/** La entidad decide; el servicio orquesta. */
@Entity
public class Pedido {
    @Id private Long id;
    @ManyToOne(fetch = FetchType.LAZY) private Cliente cliente;
    private long total;
    @Enumerated(EnumType.STRING) private EstadoPedido estado = EstadoPedido.BORRADOR;

    protected Pedido() { }

    public Pedido(Long id, Cliente cliente, long total) {
        this.id = id;
        this.cliente = cliente;
        this.total = total;
    }

    /** Regla: la deuda vigente más este pedido no puede superar el cupo del cliente. */
    public void confirmar(long deudaVigente) {
        if (estado != EstadoPedido.BORRADOR) throw new IllegalStateException("Solo se confirma un borrador");
        long disponible = cliente.cupo() - deudaVigente;
        if (total > disponible) throw new CupoExcedido(disponible, total);
        estado = EstadoPedido.CONFIRMADO;
    }

    public Long id() { return id; }
    public Cliente cliente() { return cliente; }
    public long total() { return total; }
    public EstadoPedido estado() { return estado; }
}
