import java.util.List;

public final class Figuras {
    private Figuras() { }

    /** Punto 3: no conoce ningún tipo concreto. */
    public static Figura mayorArea(List<? extends Figura> figuras) {
        if (figuras.isEmpty()) throw new IllegalArgumentException("La lista está vacía");
        Figura mayor = figuras.get(0);
        for (Figura figura : figuras) {
            if (figura.area() > mayor.area()) mayor = figura;
        }
        return mayor;
    }

    public static void imprimirMayorArea(List<? extends Figura> figuras) {
        System.out.print("Mayor área -> ");
        mayorArea(figuras).describir();
    }
}
