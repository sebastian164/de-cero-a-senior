package jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class Cliente {
    @Id @GeneratedValue
    private Long id;
    private String nombre;
    private String ciudad;

    protected Cliente() { }

    public Cliente(String nombre, String ciudad) {
        this.nombre = nombre;
        this.ciudad = ciudad;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getCiudad() { return ciudad; }
}
