package jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class LineaPedido {
    @Id @GeneratedValue
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    private Pedido pedido;
    private String producto;
    private int cantidad;

    protected LineaPedido() { }

    LineaPedido(Pedido pedido, String producto, int cantidad) {
        this.pedido = pedido;
        this.producto = producto;
        this.cantidad = cantidad;
    }

    public String getProducto() { return producto; }
    public int getCantidad() { return cantidad; }
}
