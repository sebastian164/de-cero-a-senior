import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        Combo escritorio = new Combo("Kit escritorio", 10);
        escritorio.agregar(new Producto("Teclado", 120_000));
        escritorio.agregar(new Producto("Mouse", 60_000));

        Combo oficina = new Combo("Oficina completa", 5);
        oficina.agregar(escritorio);                       // un combo dentro de otro
        oficina.agregar(new Producto("Monitor", 800_000));

        System.out.println(oficina.describir(""));
        System.out.println("Total: $" + oficina.precio());
    }
}

/** El componente: lo que hojas y compuestos tienen en común. */
interface ItemCatalogo {
    long precio();
    String describir(String sangria);
}

/** La hoja. */
class Producto implements ItemCatalogo {

    private final String nombre;
    private final long precio;

    Producto(String nombre, long precio) {
        this.nombre = nombre;
        this.precio = precio;
    }

    @Override public long precio() { return precio; }
    @Override public String describir(String sangria) { return sangria + nombre + " $" + precio; }
}

/** El compuesto: contiene componentes y delega en ellos. */
class Combo implements ItemCatalogo {

    private final String nombre;
    private final int descuentoPorcentaje;
    private final List<ItemCatalogo> items = new ArrayList<>();

    Combo(String nombre, int descuentoPorcentaje) {
        this.nombre = nombre;
        this.descuentoPorcentaje = descuentoPorcentaje;
    }

    void agregar(ItemCatalogo item) {
        items.add(item);
    }

    @Override
    public long precio() {
        long suma = 0;
        for (ItemCatalogo item : items) {
            suma += item.precio();
        }
        return suma - suma * descuentoPorcentaje / 100;
    }

    @Override
    public String describir(String sangria) {
        StringBuilder texto = new StringBuilder(sangria + nombre + " (-" + descuentoPorcentaje + "%)");
        for (ItemCatalogo item : items) {
            texto.append("\n").append(item.describir(sangria + "  "));
        }
        return texto.toString();
    }
}
