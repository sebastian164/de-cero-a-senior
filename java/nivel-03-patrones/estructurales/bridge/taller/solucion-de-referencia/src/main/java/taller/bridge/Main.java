package taller.bridge;

import java.util.List;

public class Main {

    public static void main(String[] args) {
        System.out.println("Paso 1: " + combinaciones.Combinaciones.CLASES.size() + " clases por combinación");
        System.out.println(combinaciones.Combinaciones.ejemplo());

        System.out.println("Paso 2 y 3: Bridge");
        List<Formato> formatos = List.of(new Pdf(), new Excel(), new Pantalla(), new Csv());
        for (Formato f : formatos) {
            System.out.println(new ReporteCartera(f).generar());
        }
        System.out.println(new ReporteNomina(new Pantalla()).generar());
    }
}

/** Dimensión "implementación": cómo se materializa la salida. */
interface Formato {
    String titulo(String texto);
    String fila(List<String> celdas);

    /**
     * Paso 4. El formato declara si sabe resaltar con color. Los que no, degradan a una marca de texto.
     * La abstracción pide "resaltar como alerta", no "pintar en rojo": así no tiene que saber qué formato hay.
     */
    default String resaltar(String texto, Nivel nivel) {
        return nivel == Nivel.ALERTA ? "!! " + texto : texto;
    }
}

enum Nivel { NORMAL, ALERTA }

final class Pdf implements Formato {
    @Override public String titulo(String t) { return "[PDF] " + t; }
    @Override public String fila(List<String> c) { return "[PDF] " + String.join(" | ", c); }
    @Override public String resaltar(String t, Nivel n) { return n == Nivel.ALERTA ? "{color:rojo}" + t + "{/color}" : t; }
}

final class Excel implements Formato {
    @Override public String titulo(String t) { return "[XLSX] hoja '" + t + "'"; }
    @Override public String fila(List<String> c) { return "[XLSX] " + String.join("\t", c); }
    @Override public String resaltar(String t, Nivel n) { return n == Nivel.ALERTA ? "<fill FF0000>" + t + "</fill>" : t; }
}

final class Pantalla implements Formato {
    @Override public String titulo(String t) { return "== " + t + " =="; }
    @Override public String fila(List<String> c) { return String.join("  ", c); }
}

/** Paso 3: formato nuevo. Una clase; ningún reporte cambió. */
final class Csv implements Formato {
    @Override public String titulo(String t) { return "# " + t; }
    @Override public String fila(List<String> c) { return String.join(",", c); }
}

/** Dimensión "abstracción": qué reporte es. Tiene un Formato: ese es el puente. */
abstract class Reporte {
    protected final Formato formato;
    protected Reporte(Formato formato) { this.formato = formato; }

    final String generar() {
        StringBuilder sb = new StringBuilder(formato.titulo(titulo()));
        for (List<String> fila : filas()) sb.append("\n").append(formato.fila(fila));
        return sb.toString();
    }

    protected abstract String titulo();
    protected abstract List<List<String>> filas();
}

final class ReporteVentas extends Reporte {
    ReporteVentas(Formato f) { super(f); }
    @Override protected String titulo() { return "Ventas"; }
    @Override protected List<List<String>> filas() { return List.of(List.of("Teclado", "12"), List.of("Mouse", "30")); }
}

final class ReporteInventario extends Reporte {
    ReporteInventario(Formato f) { super(f); }
    @Override protected String titulo() { return "Inventario"; }
    @Override protected List<List<String>> filas() { return List.of(List.of("Teclado", "4 en bodega")); }
}

/** Paso 4: cartera vencida se resalta como alerta. */
final class ReporteCartera extends Reporte {
    ReporteCartera(Formato f) { super(f); }
    @Override protected String titulo() { return "Cartera"; }
    @Override protected List<List<String>> filas() {
        return List.of(
                List.of("Ana", formato.resaltar("vencida 90 días", Nivel.ALERTA)),
                List.of("Luis", formato.resaltar("al día", Nivel.NORMAL)));
    }
}

/** Paso 3: tipo de reporte nuevo. Una clase; ningún formato cambió. */
final class ReporteNomina extends Reporte {
    ReporteNomina(Formato f) { super(f); }
    @Override protected String titulo() { return "Nómina"; }
    @Override protected List<List<String>> filas() { return List.of(List.of("Ana", "4500000")); }
}
