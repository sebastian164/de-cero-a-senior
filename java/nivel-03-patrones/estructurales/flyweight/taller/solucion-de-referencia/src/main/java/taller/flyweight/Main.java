package taller.flyweight;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Main {

    static final int N = 200_000;

    public static void main(String[] args) {
        long pesado = Medidor.bytesRetenidos(() -> Documentos.pesado(N));
        long ligero = Medidor.bytesRetenidos(() -> Documentos.ligero(N, new FabricaFormatos()));
        System.out.printf("Sin compartir: %,d bytes (%.1f por carácter)%n", pesado, pesado / (double) N);
        System.out.printf("Con Flyweight: %,d bytes (%.1f por carácter)%n", ligero, ligero / (double) N);
        System.out.printf("Ahorro: %.0f%%%n", 100.0 * (pesado - ligero) / pesado);

        FabricaFormatos fabrica = new FabricaFormatos();
        Documentos.ligero(N, fabrica);
        System.out.println("Formatos distintos creados: " + fabrica.cantidad());

        System.out.println(EnterosEnCache.explicar());
    }
}

// ===================== Sin compartir =====================

/** Color mutable, como suele venir de una biblioteca gráfica. */
final class ColorRgb {
    int r, g, b;
    ColorRgb(int r, int g, int b) { this.r = r; this.g = g; this.b = b; }
}

final class CaracterPesado {
    final char letra;
    final String fuente;
    final int tamano;
    final ColorRgb color;
    final int fila, columna;
    CaracterPesado(char letra, String fuente, int tamano, ColorRgb color, int fila, int columna) {
        this.letra = letra; this.fuente = fuente; this.tamano = tamano; this.color = color; this.fila = fila; this.columna = columna;
    }
}

// ===================== Flyweight =====================

/** Estado intrínseco: compartido e INMUTABLE (paso 3). Un record no tiene setters y sus campos son final. */
record Formato(String fuente, int tamano, int rgb) {
    /** Cambiar el color no modifica el compartido: devuelve otro formato (que se pide a la fábrica). */
    Formato conColor(int nuevoRgb) { return new Formato(fuente, tamano, nuevoRgb); }
}

/** Fábrica con caché; segura entre hilos. */
final class FabricaFormatos {
    private final Map<Formato, Formato> cache = new ConcurrentHashMap<>();
    Formato obtener(String fuente, int tamano, int rgb) {
        Formato f = new Formato(fuente, tamano, rgb);
        return cache.computeIfAbsent(f, k -> k);
    }
    Formato obtener(Formato f) { return cache.computeIfAbsent(f, k -> k); }
    int cantidad() { return cache.size(); }
}

/** Estado extrínseco: posición y letra; el formato es una referencia compartida. */
final class Caracter {
    final char letra;
    final int fila, columna;
    final Formato formato;
    Caracter(char letra, int fila, int columna, Formato formato) {
        this.letra = letra; this.fila = fila; this.columna = columna; this.formato = formato;
    }
}

final class Documentos {
    private static final String TEXTO = "El patrón Flyweight separa el estado intrínseco del extrínseco. ";
    private Documentos() { }

    /** Cada carácter con su propia copia de fuente y color (como si vinieran de un parser). */
    static CaracterPesado[] pesado(int n) {
        CaracterPesado[] doc = new CaracterPesado[n];
        for (int i = 0; i < n; i++) {
            boolean titulo = i % 1000 < 40;
            doc[i] = new CaracterPesado(TEXTO.charAt(i % TEXTO.length()),
                    new String(titulo ? "Georgia" : "Arial"), titulo ? 18 : 11,
                    titulo ? new ColorRgb(0x33, 0x33, 0x99) : new ColorRgb(0, 0, 0), i / 80, i % 80);
        }
        return doc;
    }

    static Caracter[] ligero(int n, FabricaFormatos fabrica) {
        Caracter[] doc = new Caracter[n];
        for (int i = 0; i < n; i++) {
            boolean titulo = i % 1000 < 40;
            Formato f = fabrica.obtener(new String(titulo ? "Georgia" : "Arial"), titulo ? 18 : 11, titulo ? 0x333399 : 0);
            doc[i] = new Caracter(TEXTO.charAt(i % TEXTO.length()), i / 80, i % 80, f);
        }
        return doc;
    }
}

final class Medidor {
    private Medidor() { }

    static long usada() {
        Runtime rt = Runtime.getRuntime();
        for (int i = 0; i < 5; i++) {
            System.gc();
            try { Thread.sleep(20); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        return rt.totalMemory() - rt.freeMemory();
    }

    /** Memoria que queda ocupada mientras el resultado sigue vivo. */
    static long bytesRetenidos(java.util.function.Supplier<Object> crear) {
        long antes = usada();
        Object vivo = crear.get();
        long despues = usada();
        java.lang.ref.Reference.reachabilityFence(vivo);
        return despues - antes;
    }
}

// ===================== Paso 4 =====================

final class EnterosEnCache {
    private EnterosEnCache() { }

    static String explicar() {
        Integer a = 127, b = 127;     // autoboxing = Integer.valueOf(127): sale de la caché [-128, 127]
        Integer c = 128, d = 128;     // Integer.valueOf(128): objetos nuevos
        return "127 == 127: " + (a == b) + " | 128 == 128: " + (c == d) + " | 128 equals 128: " + c.equals(d);
    }

    /** El error real: comparar identificadores en caja con ==. Funciona en pruebas con ids pequeños y falla en producción. */
    static boolean mismoCliente(Integer idPedido, Integer idSesion) {
        return idPedido == idSesion;   // INCORRECTO a propósito
    }
}
