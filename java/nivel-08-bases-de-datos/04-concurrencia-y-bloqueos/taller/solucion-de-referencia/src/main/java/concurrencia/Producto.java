package concurrencia;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;

@Entity
@Table(name = "productos")
public class Producto {
    @Id
    private Long id;
    private String nombre;
    private BigDecimal precio;
    private int stock;
    @Version
    private long version;      // Hibernate lo incrementa y lo comprueba en cada UPDATE

    protected Producto() {
    }

    public Producto(Long id, String nombre, BigDecimal precio, int stock) {
        this.id = id;
        this.nombre = nombre;
        this.precio = precio;
        this.stock = stock;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public BigDecimal getPrecio() { return precio; }
    public int getStock() { return stock; }
    public long getVersion() { return version; }
    public void fijarPrecio(BigDecimal precio) { this.precio = precio; }

    public void descontar(int n) {
        if (n > stock) {
            throw new IllegalStateException("Stock insuficiente");
        }
        stock -= n;
    }
}
