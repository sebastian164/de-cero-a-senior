package tienda;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** El sobre de órdenes y respuestas (sesión 05 de Kafka): id estable, saga, tipo, versión y los datos que el destino necesita. */
public final class Mensajes {

    static final ObjectMapper JSON = new ObjectMapper();

    public static final String ORDENES_INVENTARIO = "compra.ordenes.inventario";
    public static final String ORDENES_PAGOS = "compra.ordenes.pagos";
    public static final String ORDENES_ENVIOS = "compra.ordenes.envios";
    public static final String RESPUESTAS = "compra.respuestas";
    public static final String FALLIDOS = "compra.fallidos";
    public static final String RESULTADOS = "compra.resultados";

    private Mensajes() {
    }

    public static String temaDeOrdenes(String destino) {
        return "compra.ordenes." + destino;
    }

    public record Orden(String id, String sagaId, String correlacion, String tipo, long centavos, String producto, int cantidad, String ciudad) {
        public String json() {
            return "{\"id\":\"" + id + "\",\"version\":1,\"sagaId\":\"" + sagaId + "\",\"correlacion\":\"" + correlacion + "\",\"tipo\":\"" + tipo + "\",\"datos\":{\"centavos\":" + centavos
                    + ",\"producto\":\"" + producto + "\",\"cantidad\":" + cantidad + ",\"ciudad\":\"" + ciudad + "\"}}";
        }

        public static Orden leer(String json) {
            try {
                JsonNode n = JSON.readTree(json);
                JsonNode d = n.get("datos");
                return new Orden(n.get("id").asText(), n.get("sagaId").asText(), n.path("correlacion").asText(n.get("sagaId").asText()), n.get("tipo").asText(), d.get("centavos").asLong(),
                        d.get("producto").asText(), d.get("cantidad").asInt(), d.get("ciudad").asText());
            } catch (JsonProcessingException | RuntimeException e) {
                throw new MensajeIlegible("orden ilegible: " + json, e);
            }
        }
    }

    public record Respuesta(String id, String sagaId, String correlacion, String tipo) {
        public String json() {
            return "{\"id\":\"" + id + "\",\"version\":1,\"sagaId\":\"" + sagaId + "\",\"correlacion\":\"" + correlacion + "\",\"tipo\":\"" + tipo + "\"}";
        }

        public static Respuesta leer(String json) {
            try {
                JsonNode n = JSON.readTree(json);
                return new Respuesta(n.get("id").asText(), n.get("sagaId").asText(), n.path("correlacion").asText(n.get("sagaId").asText()), n.get("tipo").asText());
            } catch (JsonProcessingException | RuntimeException e) {
                throw new MensajeIlegible("respuesta ilegible: " + json, e);
            }
        }
    }

    /** No se arregla reintentando: va al tema de fallidos. */
    public static final class MensajeIlegible extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public MensajeIlegible(String m, Throwable causa) {
            super(m, causa);
        }
    }
}
