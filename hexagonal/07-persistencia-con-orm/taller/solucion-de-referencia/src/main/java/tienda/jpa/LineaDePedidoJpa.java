package tienda.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import tienda.dominio.Dinero;
import tienda.dominio.LineaDePedido;

@Entity
@Table(name = "linea_de_pedido")
public class LineaDePedidoJpa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id")
    private PedidoJpa pedido;

    @Column(nullable = false)
    private int posicion;

    @Column(nullable = false)
    private String producto;

    @Column(nullable = false)
    private int cantidad;

    @Column(name = "precio_unitario_centavos", nullable = false)
    private long precioUnitarioCentavos;

    protected LineaDePedidoJpa() {
    }

    static LineaDePedidoJpa desde(PedidoJpa pedido, int posicion, LineaDePedido linea) {
        LineaDePedidoJpa fila = new LineaDePedidoJpa();
        fila.pedido = pedido;
        fila.posicion = posicion;
        fila.producto = linea.producto();
        fila.cantidad = linea.cantidad();
        fila.precioUnitarioCentavos = linea.precioUnitario().centavos();
        return fila;
    }

    LineaDePedido aDominio() {
        return new LineaDePedido(producto, cantidad, new Dinero(precioUnitarioCentavos));
    }
}
