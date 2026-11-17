package tienda.adaptadores.salida.mongo;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import org.bson.Document;
import tienda.aplicacion.ConflictoDeConcurrencia;
import tienda.aplicacion.puertos.salida.Inventario;
import tienda.dominio.Existencias;

/** Las existencias viven en el documento del producto ({@code productos}), con una {@code version} que se exige al guardar. */
public final class InventarioMongo implements Inventario {

    private final MongoCollection<Document> productos;
    private final Op op;
    private final Map<Existencias, Long> versiones = Collections.synchronizedMap(new WeakHashMap<>());

    public InventarioMongo(MongoDatabase db, UnidadDeTrabajoMongo unidad) {
        this.productos = db.getCollection("productos");
        this.op = new Op(unidad);
    }

    @Override
    public Optional<Existencias> existenciasDe(String producto) {
        return Errores.proteger(() -> Optional.ofNullable(op.buscar(productos, eq("_id", producto)).first())
                .filter(d -> d.get("existencias") != null)
                .map(d -> {
                    Existencias existencias = Existencias.de(producto, d.getInteger("existencias"));
                    versiones.put(existencias, d.get("version", 0L));
                    return existencias;
                }));
    }

    @Override
    public void guardar(Existencias existencias) {
        Errores.proteger(() -> {
            Long leida = versiones.get(existencias);
            Document devuelto;
            if (leida != null) {
                devuelto = op.modificar(productos, and(eq("_id", existencias.producto()), eq("version", leida)),
                        Updates.combine(Updates.set("existencias", existencias.unidades()), Updates.inc("version", 1L)),
                        new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
                if (devuelto == null) {
                    throw new ConflictoDeConcurrencia("las existencias de " + existencias.producto());
                }
            } else {                                                  // sembrar: se escribe sin comparar
                devuelto = op.modificar(productos, eq("_id", existencias.producto()),
                        Updates.combine(Updates.set("existencias", existencias.unidades()), Updates.inc("version", 1L)),
                        new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
            }
            versiones.put(existencias, devuelto.get("version", 0L));
        });
    }
}
