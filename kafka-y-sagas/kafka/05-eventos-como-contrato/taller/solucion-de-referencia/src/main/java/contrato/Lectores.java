package contrato;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * El lado del consumidor: lectores de PedidoConfirmado de distintas versiones. Todos son "lectores tolerantes":
 * leen solo lo que usan, ignoran lo que no conocen y exigen lo que de verdad necesitan.
 */
public final class Lectores {

    private Lectores() {
    }

    public enum Moneda {
        COP, USD, DESCONOCIDA;

        /** Tolerante con los valores nuevos: un valor que no conoce no rompe, se marca para tratarlo aparte. */
        public static Moneda desde(String texto) {
            for (Moneda m : values()) if (m.name().equals(texto)) return m;
            return DESCONOCIDA;
        }

        /** Estricto: así se escribe el consumidor que no piensa en la evolución. */
        public static Moneda estricto(String texto) {
            return valueOf(texto);
        }
    }

    public record Pedido(String pedidoId, String clienteId, long totalCentavos, Moneda moneda, String canal) {
    }

    public static final class EventoIlegible extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public EventoIlegible(String motivo) {
            super(motivo);
        }
    }

    private static JsonNode exigir(JsonNode nodo, String campo) {
        JsonNode v = nodo.get(campo);
        if (v == null || v.isNull()) throw new EventoIlegible("falta el campo " + campo);
        return v;
    }

    /** Consumidor de la versión 1: escrito cuando solo existía totalCentavos. Nunca se recompiló. */
    public static Pedido leerV1(String json) {
        JsonNode datos = exigir(Eventos.read(json), "datos");
        return new Pedido(exigir(datos, "pedidoId").asText(), exigir(datos, "clienteId").asText(),
                exigir(datos, "totalCentavos").asLong(), Moneda.desde(exigir(datos, "moneda").asText()), null);
    }

    /** Igual que leerV1, pero con la enumeración estricta (el error del paso 9). */
    public static Pedido leerV1Estricto(String json) {
        JsonNode datos = exigir(Eventos.read(json), "datos");
        return new Pedido(exigir(datos, "pedidoId").asText(), exigir(datos, "clienteId").asText(),
                exigir(datos, "totalCentavos").asLong(), Moneda.estricto(exigir(datos, "moneda").asText()), null);
    }

    /** Consumidor ya migrado: lee total y, si no está (eventos viejos), totalCentavos; canal es opcional con valor por defecto. */
    public static Pedido leerV2(String json) {
        JsonNode datos = exigir(Eventos.read(json), "datos");
        JsonNode total = datos.has("total") ? datos.get("total") : datos.get("totalCentavos");
        if (total == null) throw new EventoIlegible("falta total (ni totalCentavos)");
        JsonNode canal = datos.get("canal");
        return new Pedido(exigir(datos, "pedidoId").asText(), exigir(datos, "clienteId").asText(), total.asLong(),
                Moneda.desde(exigir(datos, "moneda").asText()), canal == null ? "web" : canal.asText());
    }
}
