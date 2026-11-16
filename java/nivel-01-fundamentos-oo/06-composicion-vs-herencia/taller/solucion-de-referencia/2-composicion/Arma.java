public record Arma(String nombre, int dano) {
    public static final Arma ESPADA = new Arma("espada", 12);
    public static final Arma ARCO = new Arma("arco", 9);
    public static final Arma BASTON = new Arma("bastón", 6);
    public static final Arma HACHA = new Arma("hacha", 14);
    public static final Arma DAGA = new Arma("daga", 7);

    public Arma {
        if (dano < 0) throw new IllegalArgumentException("El daño no puede ser negativo");
    }
}
