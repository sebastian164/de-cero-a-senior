package banco.transferencias.dominio;

public record Dinero(long centavos) implements Comparable<Dinero> {
    public static final Dinero CERO = new Dinero(0);
    public Dinero { if (centavos < 0) throw new IllegalArgumentException("monto negativo"); }
    public static Dinero pesos(long pesos) { return new Dinero(pesos * 100); }
    public Dinero mas(Dinero otro) { return new Dinero(centavos + otro.centavos); }
    public Dinero menos(Dinero otro) { return new Dinero(centavos - otro.centavos); }
    public boolean esCero() { return centavos == 0; }
    @Override public int compareTo(Dinero o) { return Long.compare(centavos, o.centavos); }
}
