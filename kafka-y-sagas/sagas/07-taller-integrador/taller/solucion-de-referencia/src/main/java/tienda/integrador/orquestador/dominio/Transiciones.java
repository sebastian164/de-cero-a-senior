package tienda.integrador.orquestador.dominio;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * La tabla de transiciones de ANALISIS.md, como datos. Es una función pura: (estado, respuesta) → paso.
 * Lo que no está en la tabla se ignora: respuestas repetidas, tardías o fuera de lugar.
 */
public final class Transiciones {

    /** Nuevo estado y, si hace falta, la orden que hay que mandar y a quién. */
    public record Paso(Estado nuevoEstado, String destino, String orden) {
        static Paso a(Estado estado, String destino, String orden) {
            return new Paso(estado, destino, orden);
        }

        static Paso fin(Estado estado) {
            return new Paso(estado, null, null);
        }
    }

    private final Map<Estado, Map<String, Paso>> tabla = new EnumMap<>(Estado.class);

    public Transiciones(boolean conPuntos) {
        en(Estado.RESERVANDO, "inventario-reservado", Paso.a(Estado.COBRANDO, "pagos", "cobrar"));
        en(Estado.RESERVANDO, "sin-existencias", Paso.fin(Estado.CANCELADA));
        en(Estado.COBRANDO, "pago-aprobado", Paso.a(Estado.PROGRAMANDO_ENVIO, "envios", "programar-envio"));
        en(Estado.COBRANDO, "pago-rechazado", Paso.a(Estado.LIBERANDO, "inventario", "liberar-inventario"));
        en(Estado.PROGRAMANDO_ENVIO, "envio-imposible", Paso.a(Estado.REEMBOLSANDO, "pagos", "reembolsar"));
        // Paso 9: el punto de fidelidad es una fila más de la tabla y un participante más.
        en(Estado.PROGRAMANDO_ENVIO, "envio-programado", conPuntos
                ? Paso.a(Estado.SUMANDO_PUNTOS, "fidelidad", "sumar-puntos")
                : Paso.fin(Estado.COMPLETADA));
        en(Estado.SUMANDO_PUNTOS, "puntos-sumados", Paso.fin(Estado.COMPLETADA));
        en(Estado.REEMBOLSANDO, "pago-reembolsado", Paso.a(Estado.LIBERANDO, "inventario", "liberar-inventario"));
        en(Estado.LIBERANDO, "inventario-liberado", Paso.fin(Estado.CANCELADA));
    }

    private void en(Estado desde, String respuesta, Paso paso) {
        tabla.computeIfAbsent(desde, e -> new HashMap<>()).put(respuesta, paso);
    }

    public Optional<Paso> siguiente(Estado estado, String respuesta) {
        return Optional.ofNullable(tabla.getOrDefault(estado, Map.of()).get(respuesta));
    }

    /** Paso 6: qué hacer cuando un participante no responde tras tres intentos. Vacío = seguir insistiendo. */
    public Optional<Paso> alVencer(Estado estado) {
        return switch (estado) {
            // No sabemos si reservó: liberar es seguro porque Inventario lo tolera aunque no haya reserva.
            case RESERVANDO -> Optional.of(Paso.a(Estado.LIBERANDO, "inventario", "liberar-inventario"));
            // No sabemos si cobró: reembolsar es seguro porque Pagos deja una lápida si aún no había cobro.
            case COBRANDO -> Optional.of(Paso.a(Estado.REEMBOLSANDO, "pagos", "reembolsar"));
            // Después del pivote y en las compensaciones no se abandona: se reintenta siempre.
            default -> Optional.empty();
        };
    }

    /** La primera orden de toda saga. */
    public Paso inicio() {
        return Paso.a(Estado.RESERVANDO, "inventario", "reservar-inventario");
    }
}
