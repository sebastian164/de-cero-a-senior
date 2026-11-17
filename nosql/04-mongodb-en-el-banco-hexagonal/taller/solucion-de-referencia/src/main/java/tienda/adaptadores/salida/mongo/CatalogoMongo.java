package tienda.adaptadores.salida.mongo;

import static com.mongodb.client.model.Filters.eq;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import java.util.Optional;
import nosql.aplicacion.AdministrarCatalogo;
import org.bson.Document;
import tienda.aplicacion.ProductoDesconocido;
import tienda.aplicacion.puertos.salida.Catalogo;
import tienda.dominio.Dinero;

/** El precio vive en el documento del producto. Cambiarlo no toca la versión de las existencias: no compite con las ventas. */
public final class CatalogoMongo implements Catalogo, AdministrarCatalogo {

    private final MongoCollection<Document> productos;
    private final Op op;

    public CatalogoMongo(MongoDatabase db, UnidadDeTrabajoMongo unidad) {
        this.productos = db.getCollection("productos");
        this.op = new Op(unidad);
    }

    @Override
    public Optional<Dinero> precioDe(String producto) {
        return Errores.proteger(() -> Optional.ofNullable(op.buscar(productos, eq("_id", producto)).first())
                .map(d -> d.getLong("precio")).map(Dinero::new));
    }

    @Override
    public void definir(String producto, String nombre, Dinero precio) {
        Errores.proteger(() -> op.actualizar(productos, eq("_id", producto),
                Updates.combine(Updates.set("nombre", nombre), Updates.set("precio", precio.centavos())), new UpdateOptions().upsert(true)));
    }

    @Override
    public void cambiarPrecio(String producto, Dinero precio) {
        Errores.proteger(() -> {
            if (op.actualizar(productos, eq("_id", producto), Updates.set("precio", precio.centavos()), new UpdateOptions()).getMatchedCount() == 0) {
                throw new ProductoDesconocido(producto);
            }
        });
    }
}
