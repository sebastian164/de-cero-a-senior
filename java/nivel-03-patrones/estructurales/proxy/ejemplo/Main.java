import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Main {

    public static void main(String[] args) {
        Reportes reportes = new ReportesConPermisos(
                new ReportesConCache(new ReportesReales()), Set.of("ana"));

        System.out.println(reportes.ventasDelAnio("ana", 2025));
        System.out.println(reportes.ventasDelAnio("ana", 2025));   // sale de la caché
        try {
            reportes.ventasDelAnio("luis", 2025);
        } catch (SecurityException e) {
            System.out.println("Acceso denegado: " + e.getMessage());
        }
    }
}

interface Reportes {
    String ventasDelAnio(String usuario, int anio);
}

/** El objeto real: solo negocio. */
class ReportesReales implements Reportes {
    @Override
    public String ventasDelAnio(String usuario, int anio) {
        System.out.println("  (calculando " + anio + ", operación costosa)");
        return "Ventas " + anio + ": $1.250 millones";
    }
}

/** Proxy de caché: puede responder sin llamar al objeto real. */
class ReportesConCache implements Reportes {

    private final Reportes real;
    private final Map<Integer, String> cache = new HashMap<>();

    ReportesConCache(Reportes real) {
        this.real = real;
    }

    @Override
    public String ventasDelAnio(String usuario, int anio) {
        return cache.computeIfAbsent(anio, a -> real.ventasDelAnio(usuario, a));
    }
}

/** Proxy de protección: decide si la llamada pasa. */
class ReportesConPermisos implements Reportes {

    private final Reportes real;
    private final Set<String> autorizados;

    ReportesConPermisos(Reportes real, Set<String> autorizados) {
        this.real = real;
        this.autorizados = autorizados;
    }

    @Override
    public String ventasDelAnio(String usuario, int anio) {
        if (!autorizados.contains(usuario)) {
            throw new SecurityException(usuario + " no puede ver reportes");
        }
        return real.ventasDelAnio(usuario, anio);
    }
}
