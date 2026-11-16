import java.util.List;

public class Pruebas {
    private static int fallos = 0, total = 0;

    public static void main(String[] args) {
        igual("círculo", "Círculo: área 3.14, perímetro 6.28", new Circulo(1).descripcion());
        igual("rectángulo", "Rectángulo: área 6.00, perímetro 10.00", new Rectangulo(2, 3).descripcion());
        igual("triángulo", "Triángulo: área 6.00, perímetro 12.00", new Triangulo(4, 3, 3, 4, 5).descripcion());
        Figura grande = new Rectangulo(10, 10);
        igual("mayor área", grande, Figuras.mayorArea(List.of(new Circulo(1), grande, new Triangulo(4, 3, 3, 4, 5))));
        lanza("lista vacía", () -> Figuras.mayorArea(List.of()));
        lanza("radio negativo", () -> new Circulo(-1));
        lanza("lados imposibles", () -> new Triangulo(1, 1, 1, 1, 5));
        System.out.println(total + " pruebas, " + fallos + " fallos");
        if (fallos > 0) System.exit(1);
    }

    static void igual(String n, Object e, Object r) { total++; if (!e.equals(r)) { fallos++; System.out.println("FALLA " + n + ": " + e + " != " + r); } }

    static void lanza(String n, Runnable a) {
        total++;
        try { a.run(); fallos++; System.out.println("FALLA " + n + ": no lanzó"); }
        catch (IllegalArgumentException x) { }
    }
}
