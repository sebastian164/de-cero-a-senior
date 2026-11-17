package banco.adaptadores.salida.mongo;

import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.puertos.salida.UnidadDeTrabajo;
import com.mongodb.MongoException;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * El puerto de transacciones sobre MongoDB. Es el único que abre sesiones y transacciones;
 * los repositorios usan la sesión de la transacción en curso.
 */
public final class UnidadDeTrabajoMongo implements UnidadDeTrabajo {

    private static final TransactionOptions OPCIONES = TransactionOptions.builder()
            .readPreference(ReadPreference.primary())
            .readConcern(ReadConcern.SNAPSHOT)                   // todo lo que lee la transacción sale de la misma foto
            .writeConcern(WriteConcern.MAJORITY)                 // confirmada = la tiene la mayoría del replica set
            .build();
    private static final int INTENTOS_DE_CONFIRMAR = 3;

    private final MongoClient cliente;
    private final ThreadLocal<ClientSession> enCurso = new ThreadLocal<>();

    public UnidadDeTrabajoMongo(MongoClient cliente) {
        this.cliente = cliente;
    }

    @Override
    public <T> T ejecutar(Supplier<T> trabajo) {
        if (enCurso.get() != null) {
            return trabajo.get();                                // ya hay una abierta: se une a ella
        }
        try (ClientSession sesion = cliente.startSession()) {
            sesion.startTransaction(OPCIONES);
            enCurso.set(sesion);
            T resultado;
            try {
                resultado = trabajo.get();
            } catch (RuntimeException error) {
                abortar(sesion);
                throw traducir(error);
            } finally {
                enCurso.remove();
            }
            confirmar(sesion);
            return resultado;
        }
    }

    /** Para los repositorios: la sesión de la transacción en curso, o vacío si la operación va suelta. */
    Optional<ClientSession> sesionEnCurso() {
        return Optional.ofNullable(enCurso.get());
    }

    /** Si no se sabe si la confirmación llegó (se cortó la red), se puede repetir: confirmar dos veces es seguro. */
    private static void confirmar(ClientSession sesion) {
        for (int intento = 1; ; intento++) {
            try {
                sesion.commitTransaction();
                return;
            } catch (MongoException error) {
                boolean resultadoDesconocido = error.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL);
                if (!resultadoDesconocido || intento == INTENTOS_DE_CONFIRMAR) {
                    throw traducir(error);
                }
            }
        }
    }

    private static void abortar(ClientSession sesion) {
        try {
            sesion.abortTransaction();
        } catch (MongoException yaAbortada) {
            // el servidor ya la había abortado; lo pendiente se descarta igual
        }
    }

    /**
     * Un conflicto de escritura entre transacciones llega como error "transitorio": repetir la transacción entera
     * puede salir bien. Para la aplicación eso es exactamente un ConflictoDeConcurrencia, y ya sabe reintentarlo.
     */
    private static RuntimeException traducir(RuntimeException error) {
        if (error instanceof MongoException mongo && mongo.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)) {
            ConflictoDeConcurrencia conflicto = new ConflictoDeConcurrencia("una cuenta o una orden");
            conflicto.initCause(error);
            return conflicto;
        }
        return error;
    }
}
