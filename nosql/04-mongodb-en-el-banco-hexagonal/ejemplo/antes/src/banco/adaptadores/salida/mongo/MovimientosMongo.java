package banco.adaptadores.salida.mongo;

import static com.mongodb.client.model.Filters.eq;

import banco.aplicacion.puertos.salida.RegistroDeMovimientos;
import banco.dominio.Dinero;
import banco.dominio.Movimiento;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Sorts;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bson.Document;

public final class MovimientosMongo implements RegistroDeMovimientos {

    private final MongoCollection<Document> movimientos;

    public MovimientosMongo(MongoDatabase base) {
        this.movimientos = base.getCollection("movimientos");
    }

    @Override
    public void registrar(Movimiento movimiento) {
        movimientos.insertOne(new Document("cuentaId", movimiento.cuentaId())
                .append("tipo", movimiento.tipo().name())
                .append("valorCentavos", movimiento.valor().centavos())
                .append("fecha", Date.from(movimiento.fecha()))
                .append("referencia", movimiento.referencia()));
    }

    @Override
    public List<Movimiento> de(String cuentaId) {
        List<Movimiento> resultado = new ArrayList<>();
        for (Document documento : movimientos.find(eq("cuentaId", cuentaId)).sort(Sorts.ascending("fecha", "_id"))) {
            resultado.add(new Movimiento(cuentaId, Movimiento.Tipo.valueOf(documento.getString("tipo")),
                    new Dinero(documento.getLong("valorCentavos")), documento.getDate("fecha").toInstant(),
                    documento.getString("referencia")));
        }
        return resultado;
    }
}
