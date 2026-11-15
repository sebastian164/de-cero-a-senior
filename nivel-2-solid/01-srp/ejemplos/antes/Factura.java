import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Bien encapsulada, sin campos públicos, con validaciones.
 * Y aun así viola SRP: cuatro grupos de personas distintos
 * tienen motivos para pedir cambios en esta clase.
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

    // --- Reglas de negocio y tributarias ---

    public long calcularSubtotal() {
        long subtotal = 0;
        for (Item item : items) {
            subtotal += item.importe();
        }
        return subtotal;
    }

    public long calcularIva() {
        return calcularSubtotal() * 19 / 100;
    }

    public long calcularTotal() {
        return calcularSubtotal() + calcularIva();
    }

    // --- Presentación ---

    public String generarTexto() {
        StringBuilder texto = new StringBuilder();
        texto.append("FACTURA ").append(numero).append("\n");
        texto.append("Cliente: ").append(cliente).append("\n");
        texto.append("--------------------------------\n");
        for (Item item : items) {
            texto.append(item.getCantidad()).append(" x ")
                 .append(item.getDescripcion()).append("  $")
                 .append(item.importe()).append("\n");
        }
        texto.append("--------------------------------\n");
        texto.append("Subtotal: $").append(calcularSubtotal()).append("\n");
        texto.append("IVA 19%:  $").append(calcularIva()).append("\n");
        texto.append("TOTAL:    $").append(calcularTotal()).append("\n");
        return texto.toString();
    }

    // --- Persistencia ---

    public void guardar(Path carpeta) {
        try {
            Files.createDirectories(carpeta);
            Files.writeString(carpeta.resolve("factura-" + numero + ".txt"), generarTexto());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la factura " + numero, e);
        }
    }

    // --- Comunicación con el cliente ---

    public void enviarPorCorreo() {
        // Simulado: en un sistema real aquí habría un cliente SMTP.
        System.out.println("Enviando correo a " + correoCliente);
        System.out.println("Asunto: Su factura " + numero);
        System.out.println(generarTexto());
    }
}
