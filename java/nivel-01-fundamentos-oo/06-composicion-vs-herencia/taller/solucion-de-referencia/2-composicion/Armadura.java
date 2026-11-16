public record Armadura(String nombre, int reduccion) {
    public static final Armadura NINGUNA = new Armadura("sin armadura", 0);
    public static final Armadura CUERO = new Armadura("cuero", 3);
    public static final Armadura MALLA = new Armadura("malla", 6);
    public static final Armadura PLACAS = new Armadura("placas", 10);

    public int reducir(int dano) {
        return Math.max(0, dano - reduccion);
    }
}
