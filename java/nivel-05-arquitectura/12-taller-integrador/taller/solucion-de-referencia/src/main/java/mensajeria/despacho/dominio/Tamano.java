package mensajeria.despacho.dominio;

public enum Tamano {
    SOBRE(1, 0), PEQUENO(5, 2_000), MEDIANO(20, 5_000), GRANDE(60, 12_000);

    private final int litros;
    private final long recargo;

    Tamano(int litros, long recargo) { this.litros = litros; this.recargo = recargo; }
    public int litros() { return litros; }
    public long recargo() { return recargo; }
}
