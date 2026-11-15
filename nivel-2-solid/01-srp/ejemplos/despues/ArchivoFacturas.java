import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Razón para cambiar: dónde y cómo se almacenan las facturas.
 * Quién pide el cambio: infraestructura.
 */
public class ArchivoFacturas {

    private final Path carpeta;

    public ArchivoFacturas(Path carpeta) {
        this.carpeta = carpeta;
    }

    public void guardar(String numero, String contenido) {
        try {
            Files.createDirectories(carpeta);
            Files.writeString(carpeta.resolve("factura-" + numero + ".txt"), contenido);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la factura " + numero, e);
        }
    }
}
