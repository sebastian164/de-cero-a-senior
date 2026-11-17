package consumidores;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** El "negocio" del taller: anota cada pago procesado en un archivo, para poder contar después qué se procesó y cuántas veces. */
public final class ProcesadorEnArchivo implements ProcesarPago {

    private final Path archivo;

    public ProcesadorEnArchivo(Path archivo) {
        this.archivo = archivo;
    }

    @Override
    public synchronized void procesar(Pago pago) throws IOException {
        Files.writeString(archivo, pago.particion() + ":" + pago.posicion() + ":" + pago.valor() + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
