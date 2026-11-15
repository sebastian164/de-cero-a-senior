import java.util.List;

public class Main {

    public static void main(String[] args) {
        List<String> lineas = List.of("Ana;ana@correo.example", "", "sin-correo", "Luis;luis@correo.example");
        new ImportadorClientes().importar(lineas);
        new ImportadorProductos().importar(List.of("Teclado;120000", "Mouse;-5"));
    }
}

abstract class Importador {

    /** El método plantilla: fija los pasos y su orden. Es final a propósito. */
    final void importar(List<String> lineas) {
        int importados = 0;
        int rechazados = 0;
        for (String linea : lineas) {
            if (linea.isBlank()) {
                continue;
            }
            String[] campos = linea.split(";");
            if (esValida(campos)) {
                guardar(campos);
                importados++;
            } else {
                rechazados++;
            }
        }
        alTerminar(importados, rechazados);
    }

    /** Pasos obligatorios: cada subclase debe definirlos. */
    protected abstract boolean esValida(String[] campos);

    protected abstract void guardar(String[] campos);

    /** Gancho (hook): paso opcional con comportamiento por defecto. */
    protected void alTerminar(int importados, int rechazados) {
        System.out.println(getClass().getSimpleName() + ": " + importados + " importados, "
                + rechazados + " rechazados");
    }
}

class ImportadorClientes extends Importador {

    @Override
    protected boolean esValida(String[] campos) {
        return campos.length == 2 && campos[1].contains("@");
    }

    @Override
    protected void guardar(String[] campos) {
        System.out.println("  cliente " + campos[0]);
    }
}

class ImportadorProductos extends Importador {

    @Override
    protected boolean esValida(String[] campos) {
        return campos.length == 2 && Long.parseLong(campos[1]) > 0;
    }

    @Override
    protected void guardar(String[] campos) {
        System.out.println("  producto " + campos[0]);
    }
}
