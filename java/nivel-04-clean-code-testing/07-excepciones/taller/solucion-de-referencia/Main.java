import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Solución del taller 07. Ejecutar con: java Main.java (termina con código 1 si alguna comprobación falla). */
public class Main {

    private static int fallos = 0;

    public static void main(String[] args) {
        List<String> lineas = List.of("Teclado;120000", "Mouse;abc", "", "Monitor");

        System.out.println("Original: " + new ImportadorOriginal().importar(lineas));
        ResultadoImportacion resultado = new Importador().importar(lineas);
        System.out.println("Nuevo:");
        System.out.println(resultado.informe());

        comprobar("importa la línea válida", resultado.importados().equals(List.of(new Producto("Teclado", 120_000))));
        comprobar("rechaza las otras tres, ninguna se pierde", resultado.rechazados().size() == 3
                && resultado.importados().size() + resultado.rechazados().size() == lineas.size());
        comprobar("precio no numérico, con número de línea y valor",
                resultado.rechazados().get(0).equals(new Rechazo(2, "Mouse;abc", "el precio no es un número entero: \"abc\"")));
        comprobar("línea vacía", resultado.rechazados().get(1).equals(new Rechazo(3, "", "la línea está vacía")));
        comprobar("falta el precio", resultado.rechazados().get(2).equals(new Rechazo(4, "Monitor", "se esperaban 2 campos (nombre;precio) y hay 1")));

        ResultadoImportacion todoMal = new Importador().importar(List.of("x"));
        comprobar("si nada se importa, el resultado no es null: lista vacía y el rechazo", todoMal.importados().isEmpty() && todoMal.rechazados().size() == 1);
        comprobar("el original devuelve null en ese caso", new ImportadorOriginal().importar(List.of("x")) == null);

        ResultadoImportacion vacio = new Importador().importar(List.of());
        comprobar("una entrada vacía es un resultado vacío", vacio.importados().isEmpty() && vacio.rechazados().isEmpty() && !vacio.tieneRechazos());

        ResultadoImportacion otros = new Importador().importar(List.of("Cable;-5", ";100", "Base;1;2", "Hub; 15000 ", "Caja;99999999999999999999"));
        comprobar("precio negativo", otros.rechazados().get(0).motivo().equals("el precio no puede ser negativo: -5"));
        comprobar("nombre vacío", otros.rechazados().get(1).motivo().equals("el nombre está vacío"));
        comprobar("campos de más", otros.rechazados().get(2).motivo().equals("se esperaban 2 campos (nombre;precio) y hay 3"));
        comprobar("los espacios alrededor del precio se toleran", otros.importados().equals(List.of(new Producto("Hub", 15_000))));
        comprobar("un precio que no cabe en long es un rechazo, no un error perdido", otros.rechazados().get(3).numeroDeLinea() == 5);

        comprobar("una lista null es un error de programación y se reporta de inmediato",
                lanza(NullPointerException.class, () -> new Importador().importar(null)));
        List<String> conNull = new ArrayList<>();
        conNull.add(null);
        comprobar("una línea null se rechaza con su número", new Importador().importar(conNull).rechazados().get(0).equals(new Rechazo(1, null, "la línea es null")));
        comprobar("los resultados no se pueden modificar desde fuera",
                lanza(UnsupportedOperationException.class, () -> resultado.rechazados().clear()));

        System.out.println(fallos == 0 ? "TODO PASA" : fallos + " FALLOS");
        System.exit(fallos == 0 ? 0 : 1);
    }

    static void comprobar(String descripcion, boolean ok) {
        System.out.println((ok ? "  PASA  " : "  FALLA ") + descripcion);
        if (!ok) {
            fallos++;
        }
    }

    static boolean lanza(Class<? extends RuntimeException> tipo, Runnable accion) {
        try {
            accion.run();
            return false;
        } catch (RuntimeException e) { // el arnés solo comprueba excepciones no verificadas; no hay catch genérico en la solución
            return tipo.isInstance(e);
        }
    }
}

record Producto(String nombre, long precio) {
}

/** Una línea que no se importó: dónde estaba, qué decía y por qué no entró. */
record Rechazo(int numeroDeLinea, String contenido, String motivo) {

    @Override
    public String toString() {
        return "línea " + numeroDeLinea + " (\"" + contenido + "\"): " + motivo;
    }
}

/** El resultado distingue lo importado de lo rechazado. Nunca es null. */
record ResultadoImportacion(List<Producto> importados, List<Rechazo> rechazados) {

    ResultadoImportacion {
        importados = List.copyOf(importados);
        rechazados = List.copyOf(rechazados);
    }

    boolean tieneRechazos() {
        return !rechazados.isEmpty();
    }

    String informe() {
        StringBuilder texto = new StringBuilder("  importados: " + importados + "\n  rechazados: " + rechazados.size());
        for (Rechazo rechazo : rechazados) {
            texto.append("\n    ").append(rechazo);
        }
        return texto.toString();
    }
}

/**
 * Política explícita: una línea inválida NO detiene la importación. Se rechaza con su número y su
 * motivo, y se sigue con la siguiente. Los errores de programación (lista null) sí se lanzan.
 */
class Importador {

    private static final String SEPARADOR = ";";
    private static final int CAMPOS_ESPERADOS = 2;

    ResultadoImportacion importar(List<String> lineas) {
        Objects.requireNonNull(lineas, "lineas");
        List<Producto> importados = new ArrayList<>();
        List<Rechazo> rechazados = new ArrayList<>();
        for (int i = 0; i < lineas.size(); i++) {
            int numeroDeLinea = i + 1;
            String linea = lineas.get(i);
            switch (analizar(linea)) {
                case Linea.Valida valida -> importados.add(valida.producto());
                case Linea.Invalida invalida -> rechazados.add(new Rechazo(numeroDeLinea, linea, invalida.motivo()));
            }
        }
        return new ResultadoImportacion(importados, rechazados);
    }

    /** El formato de la línea se conoce solo aquí: devuelve el producto o el motivo del rechazo. */
    private Linea analizar(String linea) {
        if (linea == null) {
            return new Linea.Invalida("la línea es null");
        }
        if (linea.isBlank()) {
            return new Linea.Invalida("la línea está vacía");
        }
        String[] campos = linea.split(SEPARADOR, -1);
        if (campos.length != CAMPOS_ESPERADOS) {
            return new Linea.Invalida("se esperaban 2 campos (nombre;precio) y hay " + campos.length);
        }
        if (campos[0].isBlank()) {
            return new Linea.Invalida("el nombre está vacío");
        }
        String precio = campos[1].strip();
        try {
            long valor = Long.parseLong(precio);
            if (valor < 0) {
                return new Linea.Invalida("el precio no puede ser negativo: " + valor);
            }
            return new Linea.Valida(new Producto(campos[0].strip(), valor));
        } catch (NumberFormatException e) {
            // Excepción concreta y esperada: se convierte en un rechazo con contexto, no se traga.
            return new Linea.Invalida("el precio no es un número entero: \"" + precio + "\"");
        }
    }
}

sealed interface Linea {
    record Valida(Producto producto) implements Linea {
    }

    record Invalida(String motivo) implements Linea {
    }
}

/** El código de taller/inicio, sin cambios, para comparar. */
class ImportadorOriginal {

    List<String> importar(List<String> lineas) {
        List<String> resultado = new ArrayList<>();
        for (String linea : lineas) {
            try {
                String[] campos = linea.split(";");
                long precio = Long.parseLong(campos[1]);
                resultado.add(campos[0] + "=" + precio);
            } catch (Exception e) {
                // línea mala, seguimos
            }
        }
        return resultado.isEmpty() ? null : resultado;
    }
}
