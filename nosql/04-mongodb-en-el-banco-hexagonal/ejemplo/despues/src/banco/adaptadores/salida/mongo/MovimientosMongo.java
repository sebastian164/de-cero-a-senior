package banco.adaptadores.salida.mongo;

import static com.mongodb.client.model.Filters.eq;

import banco.aplicacion.puertos.salida.RegistroDeMovimientos;
import banco.dominio.Dinero;
import banco.dominio.Movimiento;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Sorts;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bson.Document;

/** Los movimientos van en su propia colección y referencian la cuenta: crecen sin límite (sesión 01). */
public final class MovimientosMongo implements RegistroDeMovimientos {

    private final MongoCollection<Document> movimientos;
    private final UnidadDeTrabajoMongo transacciones;

    public MovimientosMongo(MongoDatabase base, UnidadDeTrabajoMongo transacciones) {
        this.movimientos = base.getCollection(EsquemaMongo.MOVIMIENTOS);
        this.transacciones = transacciones;
    }

    @Override
    public void registrar(Movimiento movimiento) {
        Document documento = new Document("cuentaId", movimiento.cuentaId())
                .append("tipo", movimiento.tipo().name())
                .append("valorCentavos", movimiento.valor().centavos())
                .append("fecha", Date.from(movimiento.fecha()))
                .append("referencia", movimiento.referencia());
        transacciones.sesionEnCurso().ifPresentOrElse(
                sesion -> movimientos.insertOne(sesion, documento),
                () -> movimientos.insertOne(documento));
    }

    @Override
    public List<Movimiento> de(String cuentaId) {
        FindIterable<Document> consulta = transacciones.sesionEnCurso()
                .map(sesion -> movimientos.find(sesion, eq("cuentaId", cuentaId)))
                .orElseGet(() -> movimientos.find(eq("cuentaId", cuentaId)));
        List<Movimiento> resultado = new ArrayList<>();
        for (Document documento : consulta.sort(Sorts.ascending("fecha", "_id"))) {
            resultado.add(new Movimiento(documento.getString("cuentaId"), Movimiento.Tipo.valueOf(documento.getString("tipo")),
                    new Dinero(documento.getLong("valorCentavos")), documento.getDate("fecha").toInstant(),
                    documento.getString("referencia")));
        }
        return resultado;
    }
}
