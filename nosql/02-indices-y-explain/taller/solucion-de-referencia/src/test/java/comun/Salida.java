package comun;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/** Escribe en consola y en salida/<archivo> (relativo al módulo que ejecuta las pruebas) lo que miden los talleres. */
public final class Salida {
    private Salida() {}

    public static synchronized void linea(String archivo, String texto) {
        System.out.println(texto);
        try {
            Path p = Path.of("salida", archivo);
            Files.createDirectories(p.getParent());
            Files.writeString(p, texto + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    public static void limpiar(String archivo) {
        try { Files.deleteIfExists(Path.of("salida", archivo)); } catch (IOException e) { throw new RuntimeException(e); }
    }

    public static double media(long[] v) { return Arrays.stream(v).average().orElse(0); }

    public static long percentil(long[] v, double p) {
        long[] c = v.clone();
        Arrays.sort(c);
        return c[(int) Math.min(c.length - 1, Math.ceil(p * c.length) - 1)];
    }
}
