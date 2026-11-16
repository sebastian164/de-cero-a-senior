package taller.abstractfactory;

import java.util.List;

/**
 * Solución del taller de Abstract Factory.
 * Productos: Encabezado, Tabla, Pie y (paso 3) Grafico.
 * Familias: HTML, texto plano y (paso 2) Markdown.
 */
public class Main {

    public static void main(String[] args) {
        for (FabricaDocumento fabrica : List.of(new FabricaHtml(), new FabricaTexto(), new FabricaMarkdown())) {
            System.out.println("=== " + fabrica.getClass().getSimpleName());
            System.out.println(new GeneradorReporte(fabrica).generar(
                    "Ventas mayo",
                    List.of("Producto", "Unidades"),
                    List.of(List.of("Teclado", "12"), List.of("Mouse", "30")),
                    List.of(12, 30),
                    "Generado por el sistema"));
        }
    }
}

// ----- Productos -----

interface Encabezado { String render(String titulo); }
interface Tabla { String render(List<String> columnas, List<List<String>> filas); }
interface Pie { String render(String texto); }
/** Producto agregado en el paso 3: obligó a tocar la interfaz y TODAS las fábricas. */
interface Grafico { String render(List<Integer> valores); }

// ----- Fábrica abstracta -----

interface FabricaDocumento {
    Encabezado crearEncabezado();
    Tabla crearTabla();
    Pie crearPie();
    Grafico crearGrafico();
}

/** Cliente: no conoce ninguna clase concreta. */
final class GeneradorReporte {

    private final FabricaDocumento fabrica;

    GeneradorReporte(FabricaDocumento fabrica) {
        this.fabrica = fabrica;
    }

    String generar(String titulo, List<String> columnas, List<List<String>> filas, List<Integer> serie, String pie) {
        return fabrica.crearEncabezado().render(titulo) + "\n"
                + fabrica.crearTabla().render(columnas, filas) + "\n"
                + fabrica.crearGrafico().render(serie) + "\n"
                + fabrica.crearPie().render(pie);
    }
}

// ----- Familia HTML -----

final class FabricaHtml implements FabricaDocumento {
    @Override public Encabezado crearEncabezado() { return titulo -> "<h1>" + titulo + "</h1>"; }
    @Override public Tabla crearTabla() {
        return (columnas, filas) -> {
            StringBuilder sb = new StringBuilder("<table><tr>");
            columnas.forEach(c -> sb.append("<th>").append(c).append("</th>"));
            sb.append("</tr>");
            for (List<String> fila : filas) {
                sb.append("<tr>");
                fila.forEach(c -> sb.append("<td>").append(c).append("</td>"));
                sb.append("</tr>");
            }
            return sb.append("</table>").toString();
        };
    }
    @Override public Pie crearPie() { return texto -> "<footer>" + texto + "</footer>"; }
    @Override public Grafico crearGrafico() {
        return valores -> "<svg data-valores=\"" + valores.stream().map(String::valueOf)
                .reduce((a, b) -> a + "," + b).orElse("") + "\"/>";
    }
}

// ----- Familia texto plano -----

final class FabricaTexto implements FabricaDocumento {
    @Override public Encabezado crearEncabezado() { return titulo -> titulo.toUpperCase() + "\n" + "=".repeat(titulo.length()); }
    @Override public Tabla crearTabla() {
        return (columnas, filas) -> {
            StringBuilder sb = new StringBuilder(String.join("\t", columnas));
            filas.forEach(f -> sb.append("\n").append(String.join("\t", f)));
            return sb.toString();
        };
    }
    @Override public Pie crearPie() { return texto -> "-- " + texto; }
    @Override public Grafico crearGrafico() {
        return valores -> {
            StringBuilder sb = new StringBuilder();
            valores.forEach(v -> sb.append("#".repeat(v / 3)).append(" ").append(v).append("\n"));
            return sb.toString().stripTrailing();
        };
    }
}

// ----- Familia Markdown (paso 2: solo se AGREGÓ esta clase) -----

final class FabricaMarkdown implements FabricaDocumento {
    @Override public Encabezado crearEncabezado() { return titulo -> "# " + titulo; }
    @Override public Tabla crearTabla() {
        return (columnas, filas) -> {
            StringBuilder sb = new StringBuilder("| " + String.join(" | ", columnas) + " |\n|");
            columnas.forEach(c -> sb.append("---|"));
            filas.forEach(f -> sb.append("\n| ").append(String.join(" | ", f)).append(" |"));
            return sb.toString();
        };
    }
    @Override public Pie crearPie() { return texto -> "_" + texto + "_"; }
    @Override public Grafico crearGrafico() {
        // Gráfico propio de la familia: una lista Markdown con barras; no depende de otra familia concreta.
        return valores -> String.join("\n", valores.stream().map(v -> "- " + v + ": " + "■".repeat(v / 3)).toList());
    }
}
