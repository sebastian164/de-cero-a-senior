import java.util.List;

public class Main {

    public static void main(String[] args) {
        List<String[]> ventas = List.of(
                new String[] {"Teclado", "120000"},
                new String[] {"Mouse", "60000"});

        ExportadorCsv csv = new ExportadorCsv();
        System.out.println(csv.exportarCsv(ventas));

        ExportadorTexto texto = new ExportadorTexto();
        System.out.println(texto.generarTextoPlano(ventas));
    }
}

class ExportadorCsv {

    String exportarCsv(List<String[]> filas) {
        if (filas.isEmpty()) {
            throw new IllegalArgumentException("No hay datos para exportar");
        }
        StringBuilder salida = new StringBuilder();
        salida.append("producto;valor\n");
        long total = 0;
        for (String[] fila : filas) {
            salida.append(fila[0]).append(";").append(fila[1]).append("\n");
            total += Long.parseLong(fila[1]);
        }
        salida.append("TOTAL;").append(total).append("\n");
        return salida.toString();
    }
}

class ExportadorTexto {

    String generarTextoPlano(List<String[]> filas) {
        if (filas.isEmpty()) {
            throw new IllegalArgumentException("No hay datos para exportar");
        }
        StringBuilder salida = new StringBuilder();
        salida.append("REPORTE DE VENTAS\n");
        long total = 0;
        for (String[] fila : filas) {
            salida.append("- ").append(fila[0]).append(": $").append(fila[1]).append("\n");
            total += Long.parseLong(fila[1]);
        }
        salida.append("Total: $").append(total).append("\n");
        return salida.toString();
    }
}
