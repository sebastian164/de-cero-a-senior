import java.util.List;

public class Main {

    public static void main(String[] args) {
        List<String[]> ventas = List.of(
                new String[] {"Teclado", "120000"},
                new String[] {"Mouse", "60000"});

        List<Exportador> exportadores = List.of(new ExportadorCsv(), new ExportadorTexto());
        for (Exportador exportador : exportadores) {
            System.out.println(exportador.exportar(ventas));
        }
    }
}

/** El contrato: lo único que el resto del sistema necesita conocer. */
interface Exportador {
    String exportar(List<String[]> filas);
}

/** La base común: los pasos que todos comparten y los huecos que cada uno llena. */
abstract class ExportadorBase implements Exportador {

    @Override
    public final String exportar(List<String[]> filas) {
        if (filas.isEmpty()) {
            throw new IllegalArgumentException("No hay datos para exportar");
        }
        StringBuilder salida = new StringBuilder(encabezado());
        long total = 0;
        for (String[] fila : filas) {
            salida.append(linea(fila[0], fila[1]));
            total += Long.parseLong(fila[1]);
        }
        return salida.append(pie(total)).toString();
    }

    protected abstract String encabezado();

    protected abstract String linea(String producto, String valor);

    protected abstract String pie(long total);
}

class ExportadorCsv extends ExportadorBase {

    @Override
    protected String encabezado() {
        return "producto;valor\n";
    }

    @Override
    protected String linea(String producto, String valor) {
        return producto + ";" + valor + "\n";
    }

    @Override
    protected String pie(long total) {
        return "TOTAL;" + total + "\n";
    }
}

class ExportadorTexto extends ExportadorBase {

    @Override
    protected String encabezado() {
        return "REPORTE DE VENTAS\n";
    }

    @Override
    protected String linea(String producto, String valor) {
        return "- " + producto + ": $" + valor + "\n";
    }

    @Override
    protected String pie(long total) {
        return "Total: $" + total + "\n";
    }
}
