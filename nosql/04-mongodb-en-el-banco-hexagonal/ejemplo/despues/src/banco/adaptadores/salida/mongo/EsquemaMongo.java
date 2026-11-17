package banco.adaptadores.salida.mongo;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import java.util.ArrayList;
import java.util.List;

/**
 * MongoDB no exige esquema, pero el banco sí tiene uno: estas colecciones y estos índices.
 * Se crean al arrancar, antes de la primera transacción, y crearlos dos veces no hace nada.
 */
public final class EsquemaMongo {

    static final String CUENTAS = "cuentas";
    static final String MOVIMIENTOS = "movimientos";
    static final String ORDENES = "ordenes_procesadas";
    static final String BANDEJA = "bandeja_de_salida";

    private EsquemaMongo() {
    }

    public static void preparar(MongoDatabase base) {
        List<String> existentes = base.listCollectionNames().into(new ArrayList<>());
        for (String coleccion : List.of(CUENTAS, MOVIMIENTOS, ORDENES, BANDEJA)) {
            if (!existentes.contains(coleccion)) {
                base.createCollection(coleccion);
            }
        }
        // El extracto de una cuenta, en orden (sesión 02). Las cuentas, las órdenes y los eventos se buscan por _id.
        base.getCollection(MOVIMIENTOS).createIndex(
                Indexes.compoundIndex(Indexes.ascending("cuentaId"), Indexes.ascending("fecha")),
                new IndexOptions().name("extracto"));
        base.getCollection(BANDEJA).createIndex(Indexes.ascending("ocurridoEn"), new IndexOptions().name("por_entregar"));
    }
}
