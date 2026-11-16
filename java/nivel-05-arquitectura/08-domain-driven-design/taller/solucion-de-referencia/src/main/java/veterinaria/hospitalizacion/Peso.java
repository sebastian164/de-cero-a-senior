package veterinaria.hospitalizacion;

/** Objeto de valor: peso en gramos. */
public record Peso(int gramos) {
    public Peso { if (gramos <= 0) throw new IllegalArgumentException("peso inválido"); }
    public static Peso kilos(double kg) { return new Peso((int) Math.round(kg * 1000)); }
}
