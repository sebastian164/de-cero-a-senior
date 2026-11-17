package tienda.adaptadores.salida.mongo;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoException;
import com.mongodb.MongoSocketException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.MongoWriteException;
import java.util.function.Supplier;
import nosql.aplicacion.ErrorDeAlmacenamiento;
import tienda.aplicacion.ConflictoDeConcurrencia;

/** El único sitio que traduce los errores de MongoDB a los de la aplicación. */
public final class Errores {

    private Errores() {
    }

    /** WriteConflict llega con la etiqueta TransientTransactionError; una clave duplicada, como DUPLICATE_KEY. El resto es técnico. */
    public static RuntimeException traducir(RuntimeException error) {
        if (error instanceof MongoException m) {
            if (error instanceof MongoTimeoutException || error instanceof MongoSocketException) {
                return new ErrorDeAlmacenamiento("el almacén no responde", error);    // no es un choque entre transacciones: no se reintenta como tal
            }
            if (m.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)) {
                return new ConflictoDeConcurrencia("una transacción");
            }
            if (error instanceof MongoWriteException w && w.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                return new ConflictoDeConcurrencia("un documento con la misma clave");
            }
            return new ErrorDeAlmacenamiento("el almacén no pudo atender la operación (código " + m.getCode() + ")", error);
        }
        return error;
    }

    public static <T> T proteger(Supplier<T> operacion) {
        try {
            return operacion.get();
        } catch (RuntimeException error) {
            throw traducir(error);
        }
    }

    public static void proteger(Runnable operacion) {
        proteger(() -> {
            operacion.run();
            return null;
        });
    }
}
