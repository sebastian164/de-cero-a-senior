package taller;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Pedido {

    @Id
    private Long id;
    private String estado;
    private long total;
    private int confirmaciones;

    @OneToMany(mappedBy = "pedido", fetch = FetchType.LAZY)
    private List<Linea> lineas = new ArrayList<>();

    protected Pedido() {
    }

    public Long getId() {
        return id;
    }

    public String getEstado() {
        return estado;
    }

    public long getTotal() {
        return total;
    }

    public List<Linea> getLineas() {
        return lineas;
    }

    public void confirmar() {
        estado = "CONFIRMADO";
        confirmaciones++;
    }
}
