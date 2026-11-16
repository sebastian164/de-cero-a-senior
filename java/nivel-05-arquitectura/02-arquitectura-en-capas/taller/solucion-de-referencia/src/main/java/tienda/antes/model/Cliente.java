package tienda.antes.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Cliente {
    @Id private Long id;
    private String nombre;
    private long cupo;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public long getCupo() { return cupo; }
    public void setCupo(long cupo) { this.cupo = cupo; }
}
