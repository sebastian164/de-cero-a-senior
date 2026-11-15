import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        Carrito carrito = new Carrito();
        carrito.getLineas().add(new Linea("Café", 2, 18_000));
        carrito.getLineas().add(new Linea("Pan", -3, 4_000));
        carrito.setTotal(1);
        carrito.setPagado(true);
        carrito.getLineas().add(new Linea("Queso", 1, 22_000));
        System.out.println("Total: " + carrito.getTotal() + " pagado: " + carrito.isPagado());
    }
}

class Linea {
    public String producto;
    public int cantidad;
    public long precioUnitario;

    Linea(String producto, int cantidad, long precioUnitario) {
        this.producto = producto;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
    }
}

class Carrito {
    private List<Linea> lineas = new ArrayList<>();
    private long total;
    private boolean pagado;

    public List<Linea> getLineas() { return lineas; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public boolean isPagado() { return pagado; }
    public void setPagado(boolean pagado) { this.pagado = pagado; }
}
