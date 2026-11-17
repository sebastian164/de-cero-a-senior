package banco.adaptadores.salida.mongo;

import static com.mongodb.client.model.Filters.eq;

import banco.aplicacion.puertos.salida.RepositorioDeCuentas;
import banco.dominio.Cuenta;
import banco.dominio.Dinero;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import java.time.LocalDate;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.ObjectId;

/** "Guardar es reemplazar el documento; si no existe, se crea". Corto, claro, y la última escritura gana. */
public final class CuentasMongo implements RepositorioDeCuentas {

    private final MongoCollection<Document> cuentas;

    public CuentasMongo(MongoDatabase base) {
        this.cuentas = base.getCollection("cuentas");
    }

    @Override
    public String siguienteId() {
        return "cuenta-" + new ObjectId().toHexString();
    }

    @Override
    public void guardar(Cuenta cuenta) {
        Document documento = new Document("_id", cuenta.id())
                .append("titular", cuenta.titular())
                .append("saldoCentavos", cuenta.saldo().centavos())
                .append("enviadoHoyCentavos", cuenta.enviadoHoy().centavos())
                .append("diaDelTope", cuenta.diaDelTope().toString())
                .append("version", cuenta.version() + 1);
        cuentas.replaceOne(eq("_id", cuenta.id()), documento, new ReplaceOptions().upsert(true));
    }

    @Override
    public Optional<Cuenta> buscar(String id) {
        return Optional.ofNullable(cuentas.find(eq("_id", id)).first())
                .map(documento -> Cuenta.existente(id, documento.getString("titular"),
                        new Dinero(documento.getLong("saldoCentavos")), new Dinero(documento.getLong("enviadoHoyCentavos")),
                        LocalDate.parse(documento.getString("diaDelTope")), documento.getLong("version")));
    }
}
