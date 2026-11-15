import java.util.List;

public class Main {

    public static void main(String[] args) {
        List<String> filas = List.of("Teclado", "Mouse");

        // Variante 1: fábrica simple. Un único lugar decide la clase concreta.
        Exportador exportador = Exportadores.para("JSON");
        System.out.println(exportador.exportar(filas));

        // Variante 2: Factory Method de GoF. La subclase decide qué crear.
        GeneradorReporte diario = new ReporteParaContabilidad();
        System.out.println(diario.generar(filas));
    }
}

interface Exportador {
    String exportar(List<String> filas);
}

class ExportadorCsv implements Exportador {
    @Override public String exportar(List<String> filas) { return String.join(";", filas); }
}

class ExportadorJson implements Exportador {
    @Override public String exportar(List<String> filas) {
        return "[\"" + String.join("\",\"", filas) + "\"]";
    }
}

/** Fábrica simple: encapsula la decisión de qué instanciar. */
final class Exportadores {

    private Exportadores() { }

    static Exportador para(String formato) {
        return switch (formato) {
            case "CSV" -> new ExportadorCsv();
            case "JSON" -> new ExportadorJson();
            default -> throw new IllegalArgumentException("Formato no soportado: " + formato);
        };
    }
}

/** Factory Method: el algoritmo es fijo; la creación se delega a la subclase. */
abstract class GeneradorReporte {

    final String generar(List<String> filas) {
        Exportador exportador = crearExportador();   // el método de fábrica
        return "REPORTE\n" + exportador.exportar(filas);
    }

    protected abstract Exportador crearExportador();
}

class ReporteParaContabilidad extends GeneradorReporte {
    @Override protected Exportador crearExportador() { return new ExportadorCsv(); }
}

class ReporteParaApi extends GeneradorReporte {
    @Override protected Exportador crearExportador() { return new ExportadorJson(); }
}
