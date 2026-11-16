package oficina.reservas.dominio;

public record SalaId(String valor) {
    public SalaId { if (valor == null || valor.isBlank()) throw new IllegalArgumentException("sala vacía"); }
}
