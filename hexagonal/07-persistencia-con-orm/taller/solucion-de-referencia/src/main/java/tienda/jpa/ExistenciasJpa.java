package tienda.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import tienda.dominio.Existencias;

@Entity
@Table(name = "existencias")
public class ExistenciasJpa {

    @Id
    private String producto;

    @Column(nullable = false)
    private int unidades;

    @Version                                                     // UPDATE … WHERE producto = ? AND version = ?
    private long version;

    protected ExistenciasJpa() {
    }

    static ExistenciasJpa nuevas(Existencias existencias) {
        ExistenciasJpa fila = new ExistenciasJpa();
        fila.producto = existencias.producto();
        fila.unidades = existencias.unidades();
        return fila;
    }

    void copiarDesde(Existencias existencias) {
        this.unidades = existencias.unidades();
    }

    Existencias aDominio() {
        return Existencias.de(producto, unidades);
    }
}
