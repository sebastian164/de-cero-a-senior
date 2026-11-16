package streams;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class Consultas {
    private Consultas() { }

    /** 1. Total por categoría, de mayor a menor (LinkedHashMap conserva el orden). */
    public static Map<String, Long> totalPorCategoria(List<Transaccion> ts) {
        return ts.stream()
                .collect(Collectors.groupingBy(Transaccion::categoria, Collectors.summingLong(Transaccion::valor)))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    /** 2. Cliente con más transacciones en el último mes (los 30 días previos a hoy, inclusive). Empate: orden alfabético. */
    public static Optional<String> clienteMasActivo(List<Transaccion> ts, LocalDate hoy) {
        LocalDate desde = hoy.minusMonths(1);
        return ts.stream()
                .filter(t -> !t.fecha().isBefore(desde) && !t.fecha().isAfter(hoy))
                .collect(Collectors.groupingBy(Transaccion::cliente, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.<String, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                .map(Map.Entry::getKey);
    }

    /** 3. Promedio de valor por tipo. */
    public static Map<Transaccion.Tipo, Double> promedioPorTipo(List<Transaccion> ts) {
        return ts.stream().collect(Collectors.groupingBy(Transaccion::tipo, Collectors.averagingLong(Transaccion::valor)));
    }

    /** 4. Particionar por umbral y contar: true = mayores al umbral. */
    public static Map<Boolean, Long> particionPorUmbral(List<Transaccion> ts, long umbral) {
        return ts.stream().collect(Collectors.partitioningBy(t -> t.valor() > umbral, Collectors.counting()));
    }

    /** 5. La consulta 1 con bucles, para comparar. */
    public static Map<String, Long> totalPorCategoriaConBucles(List<Transaccion> ts) {
        Map<String, Long> totales = new HashMap<>();
        for (Transaccion t : ts) {
            totales.merge(t.categoria(), t.valor(), Long::sum);
        }
        List<Map.Entry<String, Long>> entradas = new ArrayList<>(totales.entrySet());
        entradas.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        Map<String, Long> ordenado = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : entradas) {
            ordenado.put(e.getKey(), e.getValue());
        }
        return ordenado;
    }

    /** 6. Estado compartido en un stream paralelo: suma no atómica. */
    public static long sumaConEstadoCompartido(int n) {
        long[] acumulado = {0};
        java.util.stream.IntStream.range(0, n).parallel().forEach(i -> acumulado[0] += 1);   // carrera
        return acumulado[0];
    }

    public static long sumaCorrecta(int n) {
        return java.util.stream.IntStream.range(0, n).parallel().mapToLong(i -> 1).sum();
    }

    // 7. Tres comprobaciones de null anidadas
    public record Pais(String nombre) { }
    public record Ciudad(String nombre, Pais pais) { }
    public record Direccion(Ciudad ciudad) { }
    public record Cliente(String nombre, Direccion direccion) { }

    public static String paisConNulls(Cliente cliente) {
        if (cliente != null) {
            if (cliente.direccion() != null) {
                if (cliente.direccion().ciudad() != null) {
                    if (cliente.direccion().ciudad().pais() != null) {
                        return cliente.direccion().ciudad().pais().nombre();
                    }
                }
            }
        }
        return "Desconocido";
    }

    public static String paisConOptional(Cliente cliente) {
        return Optional.ofNullable(cliente)
                .map(Cliente::direccion)
                .map(Direccion::ciudad)
                .map(Ciudad::pais)
                .map(Pais::nombre)
                .orElse("Desconocido");
    }

    static <T> Function<T, T> identidad() { return Function.identity(); }
}
