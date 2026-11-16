package taller.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class Main {

    public static void main(String[] args) {
        // Paso 1: el reporte depende del usuario (cada gerente ve SOLO su región).
        Reportes vulnerable = new ReportesConPermisos(new ReportesConCachePorAnio(new ReportesPorRegion()), Set.of("ana", "luis"));
        System.out.println("ana:  " + vulnerable.ventasDelAnio("ana", 2025));
        System.out.println("luis: " + vulnerable.ventasDelAnio("luis", 2025) + "   <- ¡luis ve los datos de ana!");

        Reportes corregido = new ReportesConPermisos(new ReportesConCache(new ReportesPorRegion()), Set.of("ana", "luis"));
        System.out.println("corregido ana:  " + corregido.ventasDelAnio("ana", 2025));
        System.out.println("corregido luis: " + corregido.ventasDelAnio("luis", 2025));

        // Paso 2: orden invertido (caché por fuera de los permisos).
        System.out.println("invertido, intruso con clave de ana: " + Demostraciones.intrusoConCacheFuera());

        // Paso 3: carga diferida.
        Imagen img = new ImagenDiferida("mapa.png", System.out::println);
        System.out.println("Proxy creado; todavía no se cargó nada");
        img.mostrar();
        img.mostrar();

        // Paso 4: proxy dinámico genérico.
        Reportes medido = Cronometro.envolver(Reportes.class, new ReportesPorRegion(), System.out::println);
        medido.ventasDelAnio("ana", 2025);
        List<String> lista = Cronometro.envolver(List.class, List.of("a", "b"), System.out::println);
        System.out.println("size() a través del proxy: " + lista.size());
    }
}

interface Reportes {
    String ventasDelAnio(String usuario, int anio);
}

/** Objeto real cuyo resultado DEPENDE del usuario. */
final class ReportesPorRegion implements Reportes {
    private final Map<String, String> region = Map.of("ana", "Boyacá", "luis", "Antioquia", "intruso", "-");
    int calculos;
    @Override public String ventasDelAnio(String usuario, int anio) {
        calculos++;
        return "Ventas " + anio + " región " + region.get(usuario);
    }
}

/** El proxy del ejemplo: la clave es solo el año. */
final class ReportesConCachePorAnio implements Reportes {
    private final Reportes real;
    private final Map<Integer, String> cache = new HashMap<>();
    ReportesConCachePorAnio(Reportes real) { this.real = real; }
    @Override public String ventasDelAnio(String usuario, int anio) {
        return cache.computeIfAbsent(anio, a -> real.ventasDelAnio(usuario, a));
    }
}

/** Corrección: la clave incluye todo lo que afecta el resultado. */
final class ReportesConCache implements Reportes {
    private record Clave(String usuario, int anio) { }
    private final Reportes real;
    private final Map<Clave, String> cache = new HashMap<>();
    ReportesConCache(Reportes real) { this.real = real; }
    @Override public String ventasDelAnio(String usuario, int anio) {
        return cache.computeIfAbsent(new Clave(usuario, anio), k -> real.ventasDelAnio(k.usuario(), k.anio()));
    }
}

final class ReportesConPermisos implements Reportes {
    private final Reportes real;
    private final Set<String> autorizados;
    ReportesConPermisos(Reportes real, Set<String> autorizados) { this.real = real; this.autorizados = autorizados; }
    @Override public String ventasDelAnio(String usuario, int anio) {
        if (!autorizados.contains(usuario)) throw new SecurityException(usuario + " no puede ver reportes");
        return real.ventasDelAnio(usuario, anio);
    }
}

final class Demostraciones {
    private Demostraciones() { }

    /**
     * Paso 2 con la caché del ejemplo (clave = año) por FUERA de permisos:
     * ana llena la caché y después un usuario sin permiso recibe el dato sin pasar por el control.
     */
    static String intrusoConCacheFuera() {
        Reportes r = new ReportesConCachePorAnio(new ReportesConPermisos(new ReportesPorRegion(), Set.of("ana")));
        r.ventasDelAnio("ana", 2025);
        try {
            return r.ventasDelAnio("intruso", 2025);
        } catch (SecurityException e) {
            return "denegado";
        }
    }

    /** El mismo orden con la caché corregida: el intruso sí llega a permisos (clave distinta) y es rechazado. */
    static String intrusoConCacheCorregidaFuera() {
        Reportes r = new ReportesConCache(new ReportesConPermisos(new ReportesPorRegion(), Set.of("ana")));
        r.ventasDelAnio("ana", 2025);
        try {
            return r.ventasDelAnio("intruso", 2025);
        } catch (SecurityException e) {
            return "denegado";
        }
    }
}

// ===================== Paso 3: carga diferida =====================

interface Imagen { void mostrar(); }

final class ImagenReal implements Imagen {
    static int cargas;
    private final String archivo;
    private final Consumer<String> salida;
    ImagenReal(String archivo, Consumer<String> salida) {
        this.archivo = archivo;
        this.salida = salida;
        cargas++;
        salida.accept("  cargando " + archivo + " del disco (costoso)");
    }
    @Override public void mostrar() { salida.accept("  mostrando " + archivo); }
}

final class ImagenDiferida implements Imagen {
    private final String archivo;
    private final Consumer<String> salida;
    private ImagenReal real;
    ImagenDiferida(String archivo, Consumer<String> salida) { this.archivo = archivo; this.salida = salida; }
    @Override public void mostrar() {
        if (real == null) real = new ImagenReal(archivo, salida);
        real.mostrar();
    }
}

// ===================== Paso 4: proxy dinámico =====================

final class Cronometro {
    private Cronometro() { }

    @SuppressWarnings("unchecked")
    static <T> T envolver(Class<T> interfaz, T real, Consumer<String> salida) {
        InvocationHandler h = (proxy, metodo, argumentos) -> {
            long inicio = System.nanoTime();
            try {
                return metodo.invoke(real, argumentos);
            } catch (InvocationTargetException e) {
                throw e.getCause();                    // propagar la excepción original, no la envoltura
            } finally {
                salida.accept("  " + interfaz.getSimpleName() + "." + metodo.getName() + " tardó "
                        + (System.nanoTime() - inicio) / 1000 + " µs");
            }
        };
        return (T) Proxy.newProxyInstance(interfaz.getClassLoader(), new Class<?>[] {interfaz}, h);
    }
}
