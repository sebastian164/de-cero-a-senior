import java.util.List;

public class Main {
    public static void main(String[] args) {
        List<Figura> figuras = List.of(new Circulo(1), new Rectangulo(2, 3), new Triangulo(4, 3, 3, 4, 5));
        for (Figura figura : figuras) figura.describir();
        Figuras.imprimirMayorArea(figuras);
    }
}
