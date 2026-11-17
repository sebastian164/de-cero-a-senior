package comun;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Evidencia: cada prueba anota lo que midió en salida/evidencia.txt del módulo, además de imprimirlo. */
public final class Ev {

    private Ev() {
    }

    public static synchronized void nota(String texto) {
        System.out.println(texto);
        try {
            Path dir = Path.of(System.getProperty("evidencia.dir", "salida"));
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("evidencia.txt"), texto + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
