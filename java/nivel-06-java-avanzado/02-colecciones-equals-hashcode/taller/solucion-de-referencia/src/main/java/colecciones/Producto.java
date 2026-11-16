package colecciones;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * equals por id solo cuando ya existe; hashCode constante por clase.
 * Así el objeto conserva su hashCode antes y después de persistir (cuando aparece el id),
 * y dos instancias nuevas (sin id) nunca son iguales entre sí.
 */
@Entity
public class Producto {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nombre;

    protected Producto() { }
    public Producto(String nombre) { this.nombre = nombre; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Producto otro)) return false;
        return id != null && id.equals(otro.id);
    }

    @Override
    public int hashCode() {
        return Producto.class.hashCode();
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
}
