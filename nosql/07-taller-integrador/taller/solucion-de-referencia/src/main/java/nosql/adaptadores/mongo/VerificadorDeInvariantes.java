package nosql.adaptadores.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.Document;

/**
 * Recorre MongoDB y comprueba los invariantes del servicio. Devuelve las violaciones (vacío = todo bien):
 * existencias, estados y su historial, versión, claves de idempotencia, eventos y cobros.
 */
public final class VerificadorDeInvariantes {
    private VerificadorDeInvariantes() {}

    private static final Set<String> ESTADOS = Set.of("REQUIERE_APROBACION", "PENDIENTE", "CONFIRMADO", "CANCELADO");
    /** El evento que produce cada transición, según ServicioDePedidos del núcleo. */
    private static final Map<String, String> EVENTO_DE = Map.of("PENDIENTE", "PedidoAprobado", "CONFIRMADO", "PedidoConfirmado", "CANCELADO", "PedidoCancelado");

    static boolean transicionLegal(String desde, String hacia) {
        return switch (hacia) {
            case "PENDIENTE" -> desde.equals("REQUIERE_APROBACION");           // aprobar
            case "CONFIRMADO" -> desde.equals("PENDIENTE");                    // confirmar
            case "CANCELADO" -> !desde.equals("CANCELADO");                    // cancelar (cualquiera menos el ya cancelado)
            default -> false;
        };
    }

    /** @param pagos la base del servicio de pagos; las colecciones {@code iniciales} (existencias de partida) las escribe quien siembra */
    public static List<String> verificar(MongoClient mongo, String bd) {
        List<String> fallos = new ArrayList<>();
        MongoDatabase db = mongo.getDatabase(bd), pagos = mongo.getDatabase(bd + "_pagos");

        Map<String, List<Document>> eventosDe = new HashMap<>();
        Set<String> idsDeEvento = new HashSet<>();
        for (Document e : db.getCollection("bandeja").find()) {
            if (!idsDeEvento.add(e.getString("_id"))) fallos.add("evento repetido " + e.getString("_id"));
            if (!e.getString("tipo").equals("PrecioCambiado")) eventosDe.computeIfAbsent(e.getString("pedidoId"), k -> new ArrayList<>()).add(e);
        }
        Map<String, String> pedidoDeClave = new HashMap<>();
        Set<String> pedidosConClave = new HashSet<>();
        for (Document p : db.getCollection("peticiones").find()) {
            pedidoDeClave.put(p.getString("_id"), p.getString("pedidoId"));
            if (!pedidosConClave.add(p.getString("pedidoId"))) fallos.add("dos claves de idempotencia para el pedido " + p.getString("pedidoId"));
        }

        Map<String, Long> vendidas = new HashMap<>();
        Set<String> pedidos = new HashSet<>();
        Map<String, Long> totalDe = new HashMap<>();
        for (Document p : db.getCollection("pedidos").find()) {
            String id = p.getString("_id"), estado = p.getString("estado");
            pedidos.add(id);
            totalDe.put(id, p.getLong("total"));
            if (!ESTADOS.contains(estado)) fallos.add("pedido " + id + " en estado imposible " + estado);
            if (!estado.equals("CANCELADO")) for (Document l : p.getList("lineas", Document.class)) vendidas.merge(l.getString("productoId"), (long) l.getInteger("cantidad"), Long::sum);
            if (!pedidosConClave.contains(id)) fallos.add("pedido sin clave de idempotencia registrada: " + id);

            // el historial es un camino legal que termina en el estado actual, y la versión cuenta los guardados
            List<Document> historial = p.getList("historial", Document.class);
            if (historial.isEmpty() || !historial.get(historial.size() - 1).getString("estado").equals(estado)) fallos.add("el historial de " + id + " no termina en " + estado);
            if (historial.size() < 20 && p.getLong("version") != historial.size()) fallos.add("versión %d de %s distinta de los %d estados de su historial".formatted(p.getLong("version"), id, historial.size()));
            String primero = historial.get(0).getString("estado");
            if (!primero.equals("PENDIENTE") && !primero.equals("REQUIERE_APROBACION")) fallos.add("el pedido " + id + " nació en " + primero);
            for (int i = 1; i < historial.size(); i++) {
                String desde = historial.get(i - 1).getString("estado"), hacia = historial.get(i).getString("estado");
                if (!transicionLegal(desde, hacia)) fallos.add("transición imposible en %s: %s -> %s".formatted(id, desde, hacia));
            }
            // cada pedido tiene su evento por cada cosa que le pasó: PedidoCreado y uno por transición, ni más ni menos
            Map<String, Long> tipos = new HashMap<>();
            for (Document e : eventosDe.getOrDefault(id, List.of())) tipos.merge(e.getString("tipo"), 1L, Long::sum);
            Map<String, Long> esperados = new HashMap<>();
            esperados.put("PedidoCreado", 1L);
            for (int i = 1; i < historial.size(); i++) esperados.merge(EVENTO_DE.get(historial.get(i).getString("estado")), 1L, Long::sum);
            if (historial.size() < 20 && !tipos.equals(esperados)) fallos.add("eventos de %s: hay %s y deberían ser %s".formatted(id, tipos, esperados));
        }
        for (String ref : eventosDe.keySet()) if (!pedidos.contains(ref)) fallos.add("evento de un pedido que no existe: " + ref);
        for (var c : pedidoDeClave.entrySet()) if (!pedidos.contains(c.getValue())) fallos.add("la clave " + c.getKey() + " apunta a un pedido que no existe");

        for (Document ini : db.getCollection("iniciales").find()) {
            String id = ini.getString("_id");
            Document prod = db.getCollection("productos").find(new Document("_id", id)).first();
            if (prod == null) { fallos.add("producto desaparecido " + id); continue; }
            long esperado = ini.getInteger("existencias") - vendidas.getOrDefault(id, 0L), real = prod.getInteger("existencias");
            if (real != esperado) fallos.add("existencias de %s: hay %d y deberían ser %d (inicial %d - vendidas %d)".formatted(id, real, esperado, ini.getInteger("existencias"), vendidas.getOrDefault(id, 0L)));
            if (real < 0) fallos.add("existencias negativas en " + id);
        }

        // cada cobro es de una clave de idempotencia de un pedido, por su importe, una sola vez (el _id del cobro es la clave)
        Set<String> cobradas = new HashSet<>();
        for (Document c : pagos.getCollection("cobros").find()) {
            String clave = c.getString("_id"), pedido = pedidoDeClave.get(clave);
            cobradas.add(clave);
            if (pedido == null) fallos.add("cobro sin pedido: " + clave);
            else if (totalDe.get(pedido) != null && c.getLong("importe").longValue() != totalDe.get(pedido).longValue()) fallos.add("cobro de %d por el pedido %s de %d".formatted(c.getLong("importe"), pedido, totalDe.get(pedido)));
        }
        for (String clave : pedidoDeClave.keySet()) if (!cobradas.contains(clave)) fallos.add("pedido sin cobro (el cliente no reintentó): " + pedidoDeClave.get(clave));

        // el turno aplicado nunca supera al emitido
        Document emitido = db.getCollection("turnos").find(new Document("_id", "cancelar-abandonados:emitido")).first();
        Document aplicado = db.getCollection("turnos").find(new Document("_id", "cancelar-abandonados:aplicado")).first();
        if (aplicado != null && (emitido == null || aplicado.getLong("turno") > emitido.getLong("turno"))) fallos.add("turno aplicado mayor que el emitido");
        return fallos;
    }
}
