package tienda.adaptadores.salida.mongo;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import org.bson.Document;
import org.bson.types.ObjectId;
import tienda.aplicacion.ConflictoDeConcurrencia;
import tienda.aplicacion.puertos.salida.RepositorioDePedidos;
import tienda.dominio.Dinero;
import tienda.dominio.EstadoDePedido;
import tienda.dominio.LineaDePedido;
import tienda.dominio.Pedido;

/**
 * Los pedidos en la colección {@code pedidos}, con el modelo del taller 01: líneas incrustadas (con nombre y precio copiados),
 * historial de estados con tope de 20 y {@code version}. La versión no viaja en el dominio: el adaptador anota la que leyó de cada
 * pedido y la exige al guardar (concurrencia optimista), igual que {@code @Version} en JPA.
 */
public final class PedidosMongo implements RepositorioDePedidos {

    static final int TOPE_DEL_HISTORIAL = 20;

    private record Leido(long version, EstadoDePedido estado) {
    }

    private final MongoCollection<Document> pedidos;
    private final MongoCollection<Document> productos;
    private final Op op;
    private final Map<Pedido, Leido> leidos = Collections.synchronizedMap(new WeakHashMap<>());

    public PedidosMongo(MongoDatabase db, UnidadDeTrabajoMongo unidad) {
        this.pedidos = db.getCollection("pedidos");
        this.productos = db.getCollection("productos");
        this.op = new Op(unidad);
    }

    @Override
    public String siguienteId() {
        return "pedido-" + new ObjectId().toHexString();            // ordenable por creación y sin documento caliente
    }

    @Override
    public void guardar(Pedido pedido) {
        Errores.proteger(() -> {
            Leido previo = leidos.get(pedido);
            Date ahora = new Date();
            Document devuelto;
            if (previo != null) {
                var cambios = new ArrayList<org.bson.conversions.Bson>(List.of(Updates.set("estado", pedido.estado().name()), Updates.inc("version", 1L)));
                if (previo.estado() != pedido.estado()) {
                    cambios.add(new Document("$push", new Document("historial", new Document("$each", List.of(
                            new Document("estado", pedido.estado().name()).append("fecha", ahora))).append("$slice", -TOPE_DEL_HISTORIAL))));
                }
                devuelto = op.modificar(pedidos, and(eq("_id", pedido.id()), eq("version", previo.version())), Updates.combine(cambios),
                        new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
                if (devuelto == null) {
                    throw new ConflictoDeConcurrencia("el pedido " + pedido.id());
                }
            } else {
                devuelto = op.modificar(pedidos, eq("_id", pedido.id()), new Document("$set", datos(pedido))
                        .append("$inc", new Document("version", 1L))
                        .append("$setOnInsert", new Document("historial", List.of(new Document("estado", pedido.estado().name()).append("fecha", ahora)))),
                        new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
            }
            leidos.put(pedido, new Leido(devuelto.getLong("version"), pedido.estado()));
        });
    }

    @Override
    public Optional<Pedido> buscar(String pedidoId) {
        return Errores.proteger(() -> Optional.ofNullable(op.buscar(pedidos, eq("_id", pedidoId)).first()).map(this::aPedido));
    }

    @Override
    public List<Pedido> deCliente(String cliente) {
        return Errores.proteger(() -> {
            List<Pedido> resultado = new ArrayList<>();
            op.buscar(pedidos, eq("clienteId", cliente)).sort(Sorts.ascending("fecha", "_id")).forEach(d -> resultado.add(aPedido(d)));
            return resultado;
        });
    }

    /** El documento del pedido, con el nombre del producto copiado en cada línea. */
    private Document datos(Pedido pedido) {
        List<String> ids = pedido.lineas().stream().map(LineaDePedido::producto).distinct().toList();
        Map<String, String> nombres = new HashMap<>();
        op.buscar(productos, in("_id", ids)).forEach(d -> nombres.put(d.getString("_id"), d.getString("nombre")));
        List<Document> lineas = pedido.lineas().stream().map(l -> new Document("productoId", l.producto())
                .append("nombre", nombres.getOrDefault(l.producto(), l.producto())).append("cantidad", l.cantidad())
                .append("precio", l.precioUnitario().centavos())).toList();
        return new Document("clienteId", pedido.cliente()).append("estado", pedido.estado().name())
                .append("fecha", Date.from(pedido.creadoEn())).append("fechaMicros", ChronoUnit.MICROS.between(Instant.EPOCH, pedido.creadoEn()))
                .append("total", pedido.total().centavos()).append("lineas", lineas);
    }

    private Pedido aPedido(Document d) {
        List<LineaDePedido> lineas = d.getList("lineas", Document.class).stream()
                .map(l -> new LineaDePedido(l.getString("productoId"), l.getInteger("cantidad"), new Dinero(l.getLong("precio")))).toList();
        long micros = d.getLong("fechaMicros");                       // BSON guarda milisegundos; el contrato promete microsegundos
        Pedido pedido = Pedido.existente(d.getString("_id"), d.getString("clienteId"), lineas, EstadoDePedido.valueOf(d.getString("estado")),
                Instant.EPOCH.plus(micros, ChronoUnit.MICROS));
        leidos.put(pedido, new Leido(d.getLong("version"), pedido.estado()));
        return pedido;
    }
}
