import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        Inventario bodega = new Inventario();
        Producto teclado = new Producto("Teclado", 120_000, 5);
        bodega.agregar(teclado);
        bodega.agregar(new Producto("Mouse", 60_000, 0));

        teclado.vender(2);
        bodega.imprimir();

        // Dos variables, un solo objeto.
        Producto otraReferencia = teclado;
        otraReferencia.vender(1);
        System.out.println("Teclados restantes: " + teclado.getExistencias());

        // Mismos datos, objetos distintos.
        Producto copia = new Producto("Teclado", 120_000, 2);
        System.out.println("¿Mismo objeto? " + (copia == teclado));
    }
}

class Producto {

    private final String nombre;
    private final long precio;
    private int existencias;

    Producto(String nombre, long precio, int existencias) {
        this.nombre = nombre;
        this.precio = precio;
        this.existencias = existencias;
    }

    void vender(int cantidad) {
        if (cantidad > existencias) {
            throw new IllegalStateException("Sin existencias de " + nombre);
        }
        existencias -= cantidad;
    }

    int getExistencias() {
        return existencias;
    }

    String describir() {
        return nombre + " $" + precio + " x" + existencias;
    }
}

class Inventario {

    private final List<Producto> productos = new ArrayList<>();

    void agregar(Producto producto) {
        productos.add(producto);
    }

    void imprimir() {
        for (Producto producto : productos) {
            System.out.println(producto.describir());
        }
    }
}
