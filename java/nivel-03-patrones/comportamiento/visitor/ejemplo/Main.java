import java.util.List;

public class Main {

    public static void main(String[] args) {
        List<Item> carrito = List.of(new Libro(50_000), new Licor(80_000), new Electronico(200_000));

        // Versión clásica: doble despacho.
        CalculoImpuesto impuestos = new CalculoImpuesto();
        long total = 0;
        for (Item item : carrito) {
            total += item.aceptar(impuestos);
        }
        System.out.println("Impuestos (visitor): " + total);

        // Versión moderna: switch exhaustivo sobre una jerarquía sellada.
        long totalModerno = 0;
        for (Item item : carrito) {
            totalModerno += switch (item) {
                case Libro libro -> 0L;
                case Licor licor -> licor.precio() * 35 / 100;
                case Electronico electronico -> electronico.precio() * 19 / 100;
            };
        }
        System.out.println("Impuestos (switch): " + totalModerno);
    }
}

sealed interface Item permits Libro, Licor, Electronico {
    <R> R aceptar(Visitante<R> visitante);
}

record Libro(long precio) implements Item {
    @Override public <R> R aceptar(Visitante<R> visitante) { return visitante.visitar(this); }
}

record Licor(long precio) implements Item {
    @Override public <R> R aceptar(Visitante<R> visitante) { return visitante.visitar(this); }
}

record Electronico(long precio) implements Item {
    @Override public <R> R aceptar(Visitante<R> visitante) { return visitante.visitar(this); }
}

/** Una operación nueva es una implementación nueva de esta interfaz. */
interface Visitante<R> {
    R visitar(Libro libro);
    R visitar(Licor licor);
    R visitar(Electronico electronico);
}

class CalculoImpuesto implements Visitante<Long> {
    @Override public Long visitar(Libro libro) { return 0L; }
    @Override public Long visitar(Licor licor) { return licor.precio() * 35 / 100; }
    @Override public Long visitar(Electronico electronico) { return electronico.precio() * 19 / 100; }
}
