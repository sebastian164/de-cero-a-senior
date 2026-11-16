import java.util.Locale;

/** Punto 1: las tres clases como están descritas, con describir() duplicado. */
public class Main {
    public static void main(String[] args) {
        new Circulo(1).describir();
        new Rectangulo(2, 3).describir();
        new Triangulo(4, 3, 3, 4, 5).describir();
    }
}

class Circulo {
    private final double radio;
    Circulo(double radio) { this.radio = radio; }
    double area() { return Math.PI * radio * radio; }
    double perimetro() { return 2 * Math.PI * radio; }
    void describir() {
        System.out.println(String.format(Locale.ROOT, "%s: área %.2f, perímetro %.2f", "Círculo", area(), perimetro()));
    }
}

class Rectangulo {
    private final double ancho, alto;
    Rectangulo(double ancho, double alto) { this.ancho = ancho; this.alto = alto; }
    double area() { return ancho * alto; }
    double perimetro() { return 2 * (ancho + alto); }
    void describir() {
        System.out.println(String.format(Locale.ROOT, "%s: área %.2f, perímetro %.2f", "Rectángulo", area(), perimetro()));
    }
}

class Triangulo {
    private final double base, altura, ladoA, ladoB, ladoC;
    Triangulo(double base, double altura, double ladoA, double ladoB, double ladoC) {
        this.base = base; this.altura = altura; this.ladoA = ladoA; this.ladoB = ladoB; this.ladoC = ladoC;
    }
    double area() { return base * altura / 2; }
    double perimetro() { return ladoA + ladoB + ladoC; }
    void describir() {
        System.out.println(String.format(Locale.ROOT, "%s: área %.2f, perímetro %.2f", "Triángulo", area(), perimetro()));
    }
}
