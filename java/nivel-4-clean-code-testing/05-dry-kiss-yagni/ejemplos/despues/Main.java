public class Main {

    public static void main(String[] args) {
        System.out.println(Impuestos.iva(100_000, Pais.COLOMBIA));
        System.out.println(Comisiones.deVenta(100_000));
        System.out.println(ExportadorCsv.exportar(java.util.List.of("Teclado", "Mouse")));
    }
}

enum Pais {
    COLOMBIA(19), MEXICO(16);

    final int porcentajeIva;

    Pais(int porcentajeIva) {
        this.porcentajeIva = porcentajeIva;
    }
}

/** Una regla, un lugar. Cambia cuando cambia la ley. */
final class Impuestos {
    private Impuestos() { }

    static long iva(long base, Pais pais) {
        return base * pais.porcentajeIva / 100;
    }
}

/** Otra regla, otro lugar. Cambia cuando cambia la política comercial. */
final class Comisiones {
    private static final int PORCENTAJE_COMISION = 3;

    private Comisiones() { }

    static long deVenta(long base) {
        return base * PORCENTAJE_COMISION / 100;
    }
}

/** Sin interfaz ni fábrica: hay un solo formato. El día que haya dos, se extrae. */
final class ExportadorCsv {
    private ExportadorCsv() { }

    static String exportar(java.util.List<String> filas) {
        return String.join(";", filas);
    }
}
