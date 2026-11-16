import java.util.Locale;

/**
 * Lo común: el contrato area()/perimetro() y la descripción, que es idéntica para todas.
 * describir() es final: ninguna subclase debe cambiar el formato por su cuenta.
 * Lo que NO va aquí: los datos (radio, lados), porque cada figura tiene los suyos.
 */
public abstract class Figura {

    private final String nombre;

    protected Figura(String nombre) {
        this.nombre = nombre;
    }

    public abstract double area();

    public abstract double perimetro();

    public final String descripcion() {
        return String.format(Locale.ROOT, "%s: área %.2f, perímetro %.2f", nombre, area(), perimetro());
    }

    public final void describir() {
        System.out.println(descripcion());
    }

    protected static double positivo(double valor, String campo) {
        if (!(valor > 0)) throw new IllegalArgumentException(campo + " debe ser positivo");
        return valor;
    }
}
