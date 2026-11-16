public record Pedido(long base, Categoria categoria, String ciudad, boolean fragil, String cupon) {
    public Pedido {
        if (base < 0) throw new IllegalArgumentException("La base no puede ser negativa");
        if (categoria == null || ciudad == null) throw new IllegalArgumentException("Faltan datos del pedido");
    }
}
