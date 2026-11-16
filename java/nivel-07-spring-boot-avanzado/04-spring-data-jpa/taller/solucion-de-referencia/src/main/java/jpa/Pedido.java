package jpa;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Pedido {
    @Id @GeneratedValue
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)          // EAGER por defecto: se cambia a LAZY
    private Cliente cliente;
    @Enumerated(EnumType.STRING)
    private EstadoPedido estado;
    private LocalDate fecha;
    private long total;
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LineaPedido> lineas = new ArrayList<>();

    protected Pedido() { }

    public Pedido(Cliente cliente, EstadoPedido estado, LocalDate fecha, long total) {
        this.cliente = cliente;
        this.estado = estado;
        this.fecha = fecha;
        this.total = total;
    }

    public void agregarLinea(String producto, int cantidad) {
        lineas.add(new LineaPedido(this, producto, cantidad));
    }

    public Long getId() { return id; }
    public Cliente getCliente() { return cliente; }
    public EstadoPedido getEstado() { return estado; }
    public LocalDate getFecha() { return fecha; }
    public long getTotal() { return total; }
    public List<LineaPedido> getLineas() { return lineas; }
    public void setTotal(long total) { this.total = total; }
}
