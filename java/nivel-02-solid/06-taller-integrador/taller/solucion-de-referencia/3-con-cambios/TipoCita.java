import java.util.List;

public record TipoCita(String nombre, long precio, List<Requisito> requisitos) {
    public TipoCita {
        if (precio < 0) throw new IllegalArgumentException("Precio negativo");
        requisitos = List.copyOf(requisitos);
    }

    @Override
    public String toString() { return nombre; }
}
