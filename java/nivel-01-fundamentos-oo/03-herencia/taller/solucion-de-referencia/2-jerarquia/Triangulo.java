public final class Triangulo extends Figura {
    private final double base, altura, ladoA, ladoB, ladoC;
    public Triangulo(double base, double altura, double ladoA, double ladoB, double ladoC) {
        super("Triángulo");
        this.base = positivo(base, "La base");
        this.altura = positivo(altura, "La altura");
        this.ladoA = positivo(ladoA, "El lado A");
        this.ladoB = positivo(ladoB, "El lado B");
        this.ladoC = positivo(ladoC, "El lado C");
        if (ladoA + ladoB <= ladoC || ladoA + ladoC <= ladoB || ladoB + ladoC <= ladoA) {
            throw new IllegalArgumentException("Los lados no forman un triángulo");
        }
    }
    @Override public double area() { return base * altura / 2; }
    @Override public double perimetro() { return ladoA + ladoB + ladoC; }
}
