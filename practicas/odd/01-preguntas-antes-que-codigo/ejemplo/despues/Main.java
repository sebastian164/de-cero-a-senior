import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** El mismo servicio, instrumentado para responder preguntas: un evento ancho por pago. */
public class Main {

    static final List<Map<String, Object>> emitidos = new ArrayList<>();   // en un sistema real: el recolector

    public static void main(String[] args) {
        pagar("c-01", "pedido-1", 800, "tarjeta");
        pagar("c-02", "pedido-2", 5000, "tarjeta");
        pagar("c-03", "pedido-3", 300, "transferencia");
        pagar("c-04", "pedido-4", 450, "transferencia");

        System.out.println();
        System.out.println("¿Qué le pasó al pedido-2?");
        emitidos.stream().filter(e -> e.get("pedido").equals("pedido-2"))
                .forEach(e -> System.out.println("  " + e.get("resultado") + ": " + e.get("motivo")));

        long errores = emitidos.stream().filter(e -> e.get("resultado").equals("error")).count();
        System.out.println("¿Qué parte falla por culpa nuestra?");
        System.out.println("  " + errores + " de " + emitidos.size() + " (los rechazos de negocio no cuentan)");

        System.out.println("¿Qué medio de pago concentra los errores?");
        Map<Object, Long> porMedio = emitidos.stream().filter(e -> e.get("resultado").equals("error"))
                .collect(Collectors.groupingBy(e -> e.get("medio"), LinkedHashMap::new, Collectors.counting()));
        System.out.println("  " + porMedio);
    }

    static void pagar(String correlacion, String pedido, int valor, String medio) {
        long inicio = System.nanoTime();
        String resultado = "aprobado";
        String motivo = "";
        try {
            cobrar(valor, medio);
        } catch (IllegalArgumentException rechazo) {        // el sistema funcionó: el negocio dijo que no
            resultado = "rechazado";
            motivo = rechazo.getMessage();
        } catch (IllegalStateException fallo) {             // el sistema falló
            resultado = "error";
            motivo = fallo.getMessage();
        }

        Map<String, Object> evento = new LinkedHashMap<>();
        evento.put("evento", "pago.procesado");
        evento.put("correlacion", correlacion);
        evento.put("pedido", pedido);
        evento.put("medio", medio);
        evento.put("valor", valor);
        evento.put("resultado", resultado);
        evento.put("motivo", motivo);
        evento.put("duracion_ms", (System.nanoTime() - inicio) / 1_000_000);
        evento.put("version", "1.4.2");
        emitir(evento);
    }

    static void cobrar(int valor, String medio) {
        if (valor > 1000) {
            throw new IllegalArgumentException("fondos insuficientes");
        }
        if (medio.equals("transferencia")) {
            throw new IllegalStateException("el banco no responde");
        }
    }

    /** Una línea por evento, en JSON: lo que un recolector sabe leer. */
    static void emitir(Map<String, Object> evento) {
        emitidos.add(evento);
        String json = evento.entrySet().stream()
                .map(campo -> "\"" + campo.getKey() + "\":" + (campo.getValue() instanceof Number
                        ? campo.getValue().toString()
                        : "\"" + campo.getValue().toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\""))
                .collect(Collectors.joining(",", "{", "}"));
        System.out.println(json);
    }
}
