package transacciones;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventario")
public class Inventario {
    @Id
    private Long id;
    private int stock;

    protected Inventario() {
    }

    public Inventario(Long id, int stock) {
        this.id = id;
        this.stock = stock;
    }

    public int getStock() { return stock; }
    public void descontar(int n) { this.stock -= n; }
}
