package tienda.despues.clientes;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Cliente {
    @Id private Long id;
    private String nombre;
    private long cupo;

    protected Cliente() { }

    public Cliente(Long id, String nombre, long cupo) {
        this.id = id;
        this.nombre = nombre;
        this.cupo = cupo;
    }

    public Long id() { return id; }
    public String nombre() { return nombre; }
    public long cupo() { return cupo; }
}
