package contrato;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.UUID;

/** El lado del productor: construye cada evento con su sobre (id, tipo, versión, momento, correlación, causa). */
public final class Eventos {

    static final ObjectMapper JSON = new ObjectMapper();

    private Eventos() {
    }

    private static ObjectNode sobre(String tipo, int version, String correlacion, String causa) {
        ObjectNode e = JSON.createObjectNode();
        e.put("id", UUID.randomUUID().toString());
        e.put("tipo", tipo);
        e.put("version", version);
        e.put("ocurridoEn", Instant.now().toString());
        e.put("correlacion", correlacion);
        if (causa != null) e.put("causa", causa);
        return e;
    }

    private static ArrayNode lineas(ObjectNode datos, String producto, int cantidad, long precio) {
        ArrayNode l = datos.putArray("lineas");
        ObjectNode linea = l.addObject();
        linea.put("productoId", producto);
        linea.put("cantidad", cantidad);
        linea.put("precioUnitarioCentavos", precio);
        return l;
    }

    /** Versión 1: la que conocen los consumidores viejos. */
    public static String pedidoV1(String pedidoId, String clienteId, long totalCentavos, String moneda) {
        ObjectNode e = sobre("PedidoConfirmado", 1, "compra-" + pedidoId, null);
        ObjectNode d = e.putObject("datos");
        d.put("pedidoId", pedidoId);
        d.put("clienteId", clienteId);
        lineas(d, "producto-9", 2, totalCentavos / 2);
        d.put("totalCentavos", totalCentavos);
        d.put("moneda", moneda);
        return e.toString();
    }

    /** Versión 2: compatible; añade el campo opcional canal. */
    public static String pedidoV2(String pedidoId, String clienteId, long totalCentavos, String moneda, String canal) {
        ObjectNode e = (ObjectNode) read(pedidoV1(pedidoId, clienteId, totalCentavos, moneda));
        e.put("version", 2);
        ((ObjectNode) e.get("datos")).put("canal", canal);
        return e.toString();
    }

    /** Cambio incompatible hecho de golpe: totalCentavos pasa a llamarse total. */
    public static String pedidoRenombrado(String pedidoId, String clienteId, long total, String moneda) {
        ObjectNode e = (ObjectNode) read(pedidoV1(pedidoId, clienteId, total, moneda));
        e.put("version", 3);
        ObjectNode d = (ObjectNode) e.get("datos");
        d.remove("totalCentavos");
        d.put("total", total);
        return e.toString();
    }

    /** Migración, fase 1 (expandir): se publican los dos nombres a la vez. */
    public static String pedidoExpandido(String pedidoId, String clienteId, long total, String moneda) {
        ObjectNode e = (ObjectNode) read(pedidoV1(pedidoId, clienteId, total, moneda));
        e.put("version", 3);
        ((ObjectNode) e.get("datos")).put("total", total);
        return e.toString();
    }

    /** Migración, fase 2 (contraer): solo el nombre nuevo; se publica cuando ya nadie lee el viejo. */
    public static String pedidoContraido(String pedidoId, String clienteId, long total, String moneda) {
        ObjectNode e = (ObjectNode) read(pedidoRenombrado(pedidoId, clienteId, total, moneda));
        e.put("version", 4);
        return e.toString();
    }

    public static String pago(String pedidoId, long monto) {
        ObjectNode e = sobre("PagoAprobado", 1, "compra-" + pedidoId, UUID.randomUUID().toString());
        ObjectNode d = e.putObject("datos");
        d.put("pedidoId", pedidoId);
        d.put("pagoId", "pago-" + pedidoId);
        d.put("montoCentavos", monto);
        d.put("moneda", "COP");
        return e.toString();
    }

    public static String inventario(String pedidoId) {
        ObjectNode e = sobre("InventarioReservado", 1, "compra-" + pedidoId, UUID.randomUUID().toString());
        ObjectNode d = e.putObject("datos");
        d.put("pedidoId", pedidoId);
        d.put("reservaId", "reserva-" + pedidoId);
        ArrayNode l = d.putArray("lineas");
        ObjectNode linea = l.addObject();
        linea.put("productoId", "producto-9");
        linea.put("cantidad", 2);
        return e.toString();
    }

    static com.fasterxml.jackson.databind.JsonNode read(String json) {
        try {
            return JSON.readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
