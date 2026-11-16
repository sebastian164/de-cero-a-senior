public final class Rectangulo extends Figura {
    private final double ancho, alto;
    public Rectangulo(double ancho, double alto) {
        super("Rectángulo");
        this.ancho = positivo(ancho, "El ancho");
        this.alto = positivo(alto, "El alto");
    }
    @Override public double area() { return ancho * alto; }
    @Override public double perimetro() { return 2 * (ancho + alto); }
}
