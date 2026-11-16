package veterinaria.hospitalizacion;

/** Objeto de valor: dosis en microgramos, para no perder precisión. */
public record Dosis(long microgramos) {
    public Dosis { if (microgramos <= 0) throw new IllegalArgumentException("dosis inválida"); }
    public static Dosis miligramos(double mg) { return new Dosis(Math.round(mg * 1000)); }

    /** mg por kilo de peso. */
    public static Dosis porPeso(double mgPorKg, Peso peso) { return new Dosis(Math.round(mgPorKg * peso.gramos())); }

    public boolean superaA(Dosis otra) { return microgramos > otra.microgramos; }
}
