package reservas.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "canchas")
public class Cancha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Desajuste 1: en objetos lo natural sería "Sede sede"; aquí se guarda el identificador porque
    // la reserva necesita copiar sede_id y la clave foránea compuesta (id, sede_id) no tiene equivalente en JPA.
    @Column(name = "sede_id", nullable = false)
    private Long sedeId;

    @Column(name = "deporte_id", nullable = false)
    private Long deporteId;

    @Column(nullable = false, length = 60)
    private String nombre;

    @Column(nullable = false)
    private boolean activa = true;

    protected Cancha() {
    }

    public Cancha(Long sedeId, Long deporteId, String nombre) {
        this.sedeId = sedeId;
        this.deporteId = deporteId;
        this.nombre = nombre;
    }

    public Long getId() { return id; }
    public Long getSedeId() { return sedeId; }
    public String getNombre() { return nombre; }
}
