public final class Circulo extends Figura {
    private final double radio;
    public Circulo(double radio) { super("Círculo"); this.radio = positivo(radio, "El radio"); }
    @Override public double area() { return Math.PI * radio * radio; }
    @Override public double perimetro() { return 2 * Math.PI * radio; }
}
