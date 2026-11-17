package kata;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** La calculadora de cadenas, construida requisito a requisito con el ciclo rojo, verde, refactor. */
public final class Calculadora {

    private Calculadora() {
    }

    private static final String SEPARADORES_POR_DEFECTO = ",|\n";
    private static final String INICIO_DE_CABECERA = "//";
    private static final Pattern ENTRE_CORCHETES = Pattern.compile("\\[(.+?)]");
    private static final int MAXIMO_QUE_CUENTA = 1000;

    public static int sumar(String entrada) {
        if (entrada.isEmpty()) {
            return 0;
        }
        List<Integer> numeros = numeros(entrada);
        rechazarNegativos(numeros);
        return numeros.stream().filter(numero -> numero <= MAXIMO_QUE_CUENTA).mapToInt(Integer::intValue).sum();
    }

    private static List<Integer> numeros(String entrada) {
        String separadores = SEPARADORES_POR_DEFECTO;
        String cuerpo = entrada;
        if (entrada.startsWith(INICIO_DE_CABECERA)) {
            int finDeCabecera = entrada.indexOf('\n');
            separadores += "|" + separadoresDeclarados(entrada.substring(INICIO_DE_CABECERA.length(), finDeCabecera));
            cuerpo = entrada.substring(finDeCabecera + 1);
        }
        return Arrays.stream(cuerpo.split(separadores)).map(Integer::parseInt).toList();
    }

    /**
     * "x" declara un separador; "[a][bb]" declara varios, de cualquier longitud.
     * Los más largos van primero: si no, "[;][;*]" partiría "0;*0" por el ";" y dejaría "*0".
     */
    private static String separadoresDeclarados(String cabecera) {
        if (!cabecera.startsWith("[")) {
            return Pattern.quote(cabecera);
        }
        return ENTRE_CORCHETES.matcher(cabecera).results()
                .map(separador -> separador.group(1))
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
    }

    private static void rechazarNegativos(List<Integer> numeros) {
        List<Integer> negativos = numeros.stream().filter(numero -> numero < 0).toList();
        if (!negativos.isEmpty()) {
            throw new IllegalArgumentException("negativos no permitidos: "
                    + negativos.stream().map(String::valueOf).collect(Collectors.joining(", ")));
        }
    }
}
