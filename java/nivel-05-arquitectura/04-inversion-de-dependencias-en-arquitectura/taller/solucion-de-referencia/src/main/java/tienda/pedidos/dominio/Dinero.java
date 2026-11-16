package tienda.pedidos.dominio;

public record Dinero(long pesos) {
    public Dinero {
        if (pesos < 0) throw new IllegalArgumentException("monto negativo");
    }
}
