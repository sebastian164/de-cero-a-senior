package tienda.adaptadores.salida.mongo;

import com.mongodb.MongoException;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import java.util.Optional;
import java.util.function.Supplier;
import tienda.aplicacion.puertos.salida.UnidadDeTrabajo;

/** El único sitio que abre sesiones y transacciones. Los repositorios usan la sesión de la transacción en curso. */
public final class UnidadDeTrabajoMongo implements UnidadDeTrabajo {

    private static final TransactionOptions OPCIONES = TransactionOptions.builder()
            .readPreference(ReadPreference.primary()).readConcern(ReadConcern.SNAPSHOT).writeConcern(WriteConcern.MAJORITY).build();
    private static final int INTENTOS_DE_CONFIRMAR = 3;

    private final MongoClient cliente;
    private final ThreadLocal<ClientSession> enCurso = new ThreadLocal<>();

    public UnidadDeTrabajoMongo(MongoClient cliente) {
        this.cliente = cliente;
    }

    @Override
    public <T> T ejecutar(Supplier<T> trabajo) {
        if (enCurso.get() != null) {
            return trabajo.get();                                   // anidada: se une a la abierta
        }
        try (ClientSession sesion = cliente.startSession()) {
            sesion.startTransaction(OPCIONES);
            enCurso.set(sesion);
            T resultado;
            try {
                resultado = trabajo.get();
            } catch (RuntimeException error) {
                abortar(sesion);
                throw Errores.traducir(error);
            } finally {
                enCurso.remove();
            }
            confirmar(sesion);
            return resultado;
        }
    }

    public Optional<ClientSession> sesionEnCurso() {
        return Optional.ofNullable(enCurso.get());
    }

    private static void confirmar(ClientSession sesion) {
        for (int intento = 1; ; intento++) {
            try {
                sesion.commitTransaction();
                return;
            } catch (MongoException error) {
                if (!error.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL) || intento == INTENTOS_DE_CONFIRMAR) {
                    throw Errores.traducir(error);
                }
            }
        }
    }

    private static void abortar(ClientSession sesion) {
        try {
            sesion.abortTransaction();
        } catch (MongoException yaAbortada) {
            // el servidor ya la había abortado
        }
    }
}
