import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gte;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Updates;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.Document;

/** Las mismas tres operaciones, cada una con la herramienta que le toca, y una cuarta con concurrencia de verdad. */
public class Main {

    private static final Locale ES = Locale.forLanguageTag("es-CO");

    /** Una transacción lee de una foto coherente y no se confirma hasta que la mayoría del replica set la tiene. */
    private static final TransactionOptions TRANSACCION = TransactionOptions.builder()
            .readPreference(ReadPreference.primary())
            .readConcern(ReadConcern.SNAPSHOT)
            .writeConcern(WriteConcern.MAJORITY)
            .build();

    public static void main(String[] args) {
        try (MongoClient cliente = MongoClients.create("mongodb://localhost:27017/?replicaSet=rs0")) {
            MongoDatabase base = cliente.getDatabase("nosql03_despues");
            base.drop();
            MongoCollection<Document> cuentas = base.getCollection("cuentas");

            System.out.println("1. Depósitos: la suma la hace el servidor, dentro del documento");
            abrir(cuentas, "ana", 0);
            enParalelo(8, 50, () -> cuentas.updateOne(eq("_id", "ana"), Updates.inc("saldo", 1_000L)));
            System.out.printf(ES, "   esperado %,d, guardado %,d%n", 8 * 50 * 1_000, saldo(cuentas, "ana"));

            System.out.println("2a. Retiros: la regla va en el filtro de la actualización");
            abrir(cuentas, "luis", 1_000);
            AtomicInteger aprobados = new AtomicInteger();
            enParalelo(10, 1, () -> {
                if (retirarConCondicion(cuentas, "luis", 300)) {
                    aprobados.incrementAndGet();
                }
            });
            System.out.printf(ES, "   10 retiros de 300 sobre 1.000: aprobados %d, saldo final %,d%n", aprobados.get(), saldo(cuentas, "luis"));

            System.out.println("2b. Retiros: la regla se decide en Java y se guarda solo si nadie cambió la versión");
            cuentas.insertOne(new Document("_id", "sara").append("saldo", 1_000L).append("version", 1L));
            AtomicInteger aprobadosConVersion = new AtomicInteger();
            AtomicInteger conflictos = new AtomicInteger();
            enParalelo(10, 1, () -> {
                if (retirarConVersion(cuentas, "sara", 300, conflictos)) {
                    aprobadosConVersion.incrementAndGet();
                }
            });
            System.out.printf(ES, "   10 retiros de 300 sobre 1.000: aprobados %d, saldo final %,d, conflictos detectados y reintentados %d%n",
                    aprobadosConVersion.get(), saldo(cuentas, "sara"), conflictos.get());

            System.out.println("3. Transferencia en una transacción, y el proceso cae entre las dos actualizaciones");
            abrir(cuentas, "eva", 100_000);
            abrir(cuentas, "juan", 0);
            try (ClientSession sesion = cliente.startSession()) {
                sesion.withTransaction(() -> transferir(sesion, cuentas, "eva", "juan", 30_000, true), TRANSACCION);
            } catch (IllegalStateException caida) {
                System.out.println("   " + caida.getMessage());
            }
            System.out.printf(ES, "   eva %,d + juan %,d = %,d (antes había 100.000)%n",
                    saldo(cuentas, "eva"), saldo(cuentas, "juan"), saldo(cuentas, "eva") + saldo(cuentas, "juan"));

            System.out.println("4. 200 transferencias a la vez entre eva y juan, en ambos sentidos, cada una en su transacción");
            AtomicInteger intentos = new AtomicInteger();
            AtomicInteger rechazadas = new AtomicInteger();
            AtomicInteger numero = new AtomicInteger();
            enParalelo(8, 25, () -> {
                boolean deEva = numero.getAndIncrement() % 2 == 0;
                try (ClientSession sesion = cliente.startSession()) {
                    sesion.withTransaction(() -> {
                        intentos.incrementAndGet();
                        return transferir(sesion, cuentas, deEva ? "eva" : "juan", deEva ? "juan" : "eva", 1_000, false);
                    }, TRANSACCION);
                } catch (SaldoInsuficiente rechazo) {
                    rechazadas.incrementAndGet();
                }
            });
            System.out.printf(ES, "   eva %,d + juan %,d = %,d; intentos %d para 200 transferencias (%d rechazadas por saldo)%n",
                    saldo(cuentas, "eva"), saldo(cuentas, "juan"), saldo(cuentas, "eva") + saldo(cuentas, "juan"),
                    intentos.get(), rechazadas.get());
        }
    }

    /** Comprobar y descontar son una sola operación: si el saldo no alcanza, el filtro no encuentra el documento. */
    private static boolean retirarConCondicion(MongoCollection<Document> cuentas, String id, long valor) {
        return cuentas.updateOne(and(eq("_id", id), gte("saldo", valor)), Updates.inc("saldo", -valor)).getModifiedCount() == 1;
    }

    /** Bloqueo optimista: leer con la versión, decidir, y escribir solo si la versión sigue siendo la que se leyó. */
    private static boolean retirarConVersion(MongoCollection<Document> cuentas, String id, long valor, AtomicInteger conflictos) {
        while (true) {
            Document cuenta = cuentas.find(eq("_id", id)).first();
            long saldo = cuenta.getLong("saldo");
            long version = cuenta.getLong("version");
            if (saldo < valor) {
                return false;                                    // la regla, en Java, con un dato que se va a verificar al escribir
            }
            consultarAntifraude();
            long cambiados = cuentas.updateOne(and(eq("_id", id), eq("version", version)),
                    Updates.combine(Updates.set("saldo", saldo - valor), Updates.inc("version", 1L))).getModifiedCount();
            if (cambiados == 1) {
                return true;
            }
            conflictos.incrementAndGet();                        // otro escribió entre la lectura y la escritura: de nuevo
        }
    }

    private static Void transferir(ClientSession sesion, MongoCollection<Document> cuentas, String origen, String destino,
                                   long valor, boolean caerse) {
        long descontados = cuentas.updateOne(sesion, and(eq("_id", origen), gte("saldo", valor)),
                Updates.inc("saldo", -valor)).getModifiedCount();
        if (descontados == 0) {
            throw new SaldoInsuficiente(origen);
        }
        if (caerse) {
            throw new IllegalStateException("se cayó el proceso después de descontar y antes de abonar");
        }
        cuentas.updateOne(sesion, eq("_id", destino), Updates.inc("saldo", valor));
        return null;
    }

    static final class SaldoInsuficiente extends RuntimeException {

        private static final long serialVersionUID = 1L;

        SaldoInsuficiente(String cuenta) {
            super("saldo insuficiente en " + cuenta);
        }
    }

    private static void consultarAntifraude() {
        try {
            Thread.sleep(5);
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
        }
    }

    private static void abrir(MongoCollection<Document> cuentas, String id, long saldo) {
        cuentas.insertOne(new Document("_id", id).append("saldo", saldo));
    }

    private static long saldo(MongoCollection<Document> cuentas, String id) {
        return cuentas.find(eq("_id", id)).first().getLong("saldo");
    }

    /** Lanza "hilos" tareas a la vez; cada una repite la operación "veces" veces. Si alguna falla, el fallo se ve. */
    private static void enParalelo(int hilos, int veces, Runnable operacion) {
        List<Future<?>> tareas = new ArrayList<>();
        try (ExecutorService ejecutor = Executors.newFixedThreadPool(hilos)) {
            for (int h = 0; h < hilos; h++) {
                tareas.add(ejecutor.submit(() -> {
                    for (int v = 0; v < veces; v++) {
                        operacion.run();
                    }
                }));
            }
        }                                                        // al cerrarse, el ejecutor espera a todas
        for (Future<?> tarea : tareas) {
            try {
                tarea.get();
            } catch (InterruptedException | ExecutionException fallo) {
                throw new IllegalStateException(fallo);
            }
        }
    }
}
