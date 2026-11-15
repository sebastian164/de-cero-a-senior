import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lo que una factura es: sus datos y la regla que depende solo de ellos.
 * No sabe de impuestos, de formatos, de archivos ni de correos.
 */
public class Factura {

    private final String numero;
    private final String cliente;
    private final String correoCliente;
    private final List<Item> items = new ArrayList<>();

    public Factura(String numero, String cliente, String correoCliente) {
        this.numero = numero;
        this.cliente = cliente;
        this.correoCliente = correoCliente;
    }

    public void agregarItem(Item item) {
        items.add(item);
    }

    public long subtotal() {
        long subtotal = 0;
        for (Item item : items) {
            subtotal += item.importe();
        }
        return subtotal;
    }

    public String getNumero() {
        return numero;
    }

    public String getCliente() {
        return cliente;
    }

    public String getCorreoCliente() {
        return correoCliente;
    }

    public List<Item> getItems() {
        return Collections.unmodifiableList(items);
    }
}
