import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** El carrito es dueño de sus líneas, calcula su total y se cierra al pagarse. */
public class Carrito {

    private final List<Linea> lineas = new ArrayList<>();
    private boolean pagado;

    /** Mismo producto dos veces: una sola línea con la cantidad sumada. */
    public void agregar(String producto, int cantidad, long precioUnitario) {
        verificarAbierto();
        Linea nueva = new Linea(producto, cantidad, precioUnitario);
        for (int i = 0; i < lineas.size(); i++) {
            if (lineas.get(i).producto().equals(producto)) {
                lineas.set(i, lineas.get(i).sumar(cantidad, precioUnitario));
                return;
            }
        }
        lineas.add(nueva);
    }

    public void quitar(String producto) {
        verificarAbierto();
        lineas.removeIf(l -> l.producto().equals(producto));
    }

    public void pagar() {
        verificarAbierto();
        if (lineas.isEmpty()) {
            throw new IllegalStateException("No se puede pagar un carrito vacío");
        }
        pagado = true;
    }

    public long total() {
        long total = 0;
        for (Linea linea : lineas) total = Math.addExact(total, linea.importe());
        return total;
    }

    public boolean estaPagado() { return pagado; }

    public List<Linea> lineas() { return Collections.unmodifiableList(lineas); }

    private void verificarAbierto() {
        if (pagado) throw new IllegalStateException("El carrito ya está pagado y no admite cambios");
    }
}
