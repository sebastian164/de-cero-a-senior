package tienda.jpa;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import tienda.dominio.EstadoDePedido;
import tienda.dominio.Pedido;

/** La fila de un pedido y sus líneas. Sin reglas: datos y traducción. La versión vive aquí, no en el dominio. */
@Entity
@Table(name = "pedido")
public class PedidoJpa {

    @Id
    private String id;

    @Column(nullable = false)
    private String cliente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoDePedido estado;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Version
    private long version;

    // Perezosa por defecto. Quien necesita las líneas de varios pedidos las trae con "join fetch" (paso 8).
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("posicion")
    private List<LineaDePedidoJpa> lineas = new ArrayList<>();

    protected PedidoJpa() {
    }

    static PedidoJpa nuevo(Pedido pedido) {
        PedidoJpa fila = new PedidoJpa();
        fila.id = pedido.id();
        fila.cliente = pedido.cliente();
        fila.creadoEn = pedido.creadoEn();
        fila.estado = pedido.estado();
        for (int i = 0; i < pedido.lineas().size(); i++) {
            fila.lineas.add(LineaDePedidoJpa.desde(fila, i, pedido.lineas().get(i)));
        }
        return fila;
    }

    /** Las líneas no cambian después de crear el pedido: solo el estado. */
    void copiarDesde(Pedido pedido) {
        this.estado = pedido.estado();
    }

    Pedido aDominio() {
        return Pedido.existente(id, cliente, lineas.stream().map(LineaDePedidoJpa::aDominio).toList(), estado, creadoEn);
    }
}
