package tienda.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "producto")
public class ProductoJpa {

    @Id
    private String id;

    @Column(name = "precio_centavos", nullable = false)
    private long precioCentavos;

    protected ProductoJpa() {
    }

    long precioCentavos() {
        return precioCentavos;
    }
}
