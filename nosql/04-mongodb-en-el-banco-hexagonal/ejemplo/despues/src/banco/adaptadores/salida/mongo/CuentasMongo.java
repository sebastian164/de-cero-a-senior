package banco.adaptadores.salida.mongo;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.puertos.salida.RepositorioDeCuentas;
import banco.dominio.Cuenta;
import banco.dominio.Dinero;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.UpdateResult;
import java.time.LocalDate;
import java.util.Optional;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

/**
 * Una cuenta es un documento:
 *   { _id, titular, saldoCentavos, enviadoHoyCentavos, diaDelTope: "2026-11-15", version }
 * La traducción entre la cuenta y el documento vive aquí y en ningún otro sitio: el dominio no sabe que existe MongoDB.
 */
public final class CuentasMongo implements RepositorioDeCuentas {

    private final MongoCollection<Document> cuentas;
    private final UnidadDeTrabajoMongo transacciones;

    public CuentasMongo(MongoDatabase base, UnidadDeTrabajoMongo transacciones) {
        this.cuentas = base.getCollection(EsquemaMongo.CUENTAS);
        this.transacciones = transacciones;
    }

    /** Un ObjectId se genera en el cliente, sin ir a la base, y no se repite. Un contador en un documento sería un cuello de botella. */
    @Override
    public String siguienteId() {
        return "cuenta-" + new ObjectId().toHexString();
    }

    @Override
    public void guardar(Cuenta cuenta) {
        Document datos = new Document("titular", cuenta.titular())
                .append("saldoCentavos", cuenta.saldo().centavos())
                .append("enviadoHoyCentavos", cuenta.enviadoHoy().centavos())
                .append("diaDelTope", cuenta.diaDelTope().toString())
                .append("version", cuenta.version() + 1);
        if (cuenta.version() == 0) {
            insertar(datos.append("_id", cuenta.id()));
            return;
        }
        // El bloqueo optimista: solo se escribe si la versión guardada sigue siendo la que se leyó.
        Bson mismaVersion = and(eq("_id", cuenta.id()), eq("version", cuenta.version()));
        Bson cambios = new Document("$set", datos);
        UpdateResult resultado = transacciones.sesionEnCurso()
                .map(sesion -> cuentas.updateOne(sesion, mismaVersion, cambios))
                .orElseGet(() -> cuentas.updateOne(mismaVersion, cambios));
        if (resultado.getMatchedCount() == 0) {
            throw new ConflictoDeConcurrencia("la cuenta " + cuenta.id());
        }
    }

    private void insertar(Document documento) {
        try {
            transacciones.sesionEnCurso().ifPresentOrElse(
                    sesion -> cuentas.insertOne(sesion, documento),
                    () -> cuentas.insertOne(documento));
        } catch (MongoWriteException error) {
            if (error.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                throw new ConflictoDeConcurrencia("la cuenta " + documento.getString("_id"));
            }
            throw error;
        }
    }

    @Override
    public Optional<Cuenta> buscar(String id) {
        Document documento = transacciones.sesionEnCurso()
                .map(sesion -> cuentas.find(sesion, eq("_id", id)).first())
                .orElseGet(() -> cuentas.find(eq("_id", id)).first());
        return Optional.ofNullable(documento).map(CuentasMongo::aCuenta);
    }

    /** Cada lectura construye una cuenta nueva: quien la modifica no toca lo guardado. */
    private static Cuenta aCuenta(Document documento) {
        return Cuenta.existente(documento.getString("_id"), documento.getString("titular"),
                new Dinero(documento.getLong("saldoCentavos")), new Dinero(documento.getLong("enviadoHoyCentavos")),
                LocalDate.parse(documento.getString("diaDelTope")), documento.getLong("version"));
    }
}
