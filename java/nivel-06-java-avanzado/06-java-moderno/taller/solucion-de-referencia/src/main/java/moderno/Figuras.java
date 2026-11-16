package moderno;

/** 4. if/else instanceof con conversiones frente a patrones. */
public final class Figuras {
    private Figuras() { }

    public sealed interface Figura permits Circulo, Rectangulo, Triangulo { }
    public record Circulo(double radio) implements Figura { }
    public record Rectangulo(double ancho, double alto) implements Figura { }
    public record Triangulo(double base, double altura) implements Figura { }

    public static String describirAntes(Object figura) {
        if (figura instanceof Circulo) {
            Circulo c = (Circulo) figura;
            if (c.radio() > 10) return "círculo grande";
            return "círculo de radio " + c.radio();
        } else if (figura instanceof Rectangulo) {
            Rectangulo r = (Rectangulo) figura;
            if (r.ancho() == r.alto()) return "cuadrado de lado " + r.ancho();
            return "rectángulo " + r.ancho() + "x" + r.alto();
        } else if (figura instanceof Triangulo) {
            Triangulo t = (Triangulo) figura;
            return "triángulo de área " + t.base() * t.altura() / 2;
        } else {
            throw new IllegalArgumentException("desconocida");
        }
    }

    public static String describir(Figura figura) {
        return switch (figura) {
            case Circulo c when c.radio() > 10 -> "círculo grande";
            case Circulo(double radio) -> "círculo de radio " + radio;
            case Rectangulo(double ancho, double alto) when ancho == alto -> "cuadrado de lado " + ancho;
            case Rectangulo(double ancho, double alto) -> "rectángulo " + ancho + "x" + alto;
            case Triangulo(double base, double altura) -> "triángulo de área " + base * altura / 2;
        };
    }
}
