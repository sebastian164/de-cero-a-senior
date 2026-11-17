import static com.mongodb.client.model.Filters.eq;

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

/** Tres operaciones del banco escritas "como en Java": leer, decidir, escribir. Con un solo usuario funcionan. */
public class Main {

    private static final Locale ES = Locale.forLanguageTag("es-CO");

    public static void main(String[] args) {
        try (MongoClient cliente = MongoClients.create("mongodb://localhost:27017/?replicaSet=rs0")) {
            MongoDatabase base = cliente.getDatabase("nosql03_antes");
            base.drop();
            MongoCollection<Document> cuentas = base.getCollection("cuentas");

            System.out.println("1. Depósitos: leer el documento, sumar en Java y reemplazarlo");
            abrir(cuentas, "ana", 0);
            enParalelo(8, 50, () -> depositar(cuentas, "ana", 1_000));
            System.out.printf(ES, "   esperado %,d, guardado %,d%n", 8 * 50 * 1_000, saldo(cuentas, "ana"));

            System.out.println("2. Retiros: comprobar el saldo en Java y luego descontar con $inc");
            abrir(cuentas, "luis", 1_000);
            AtomicInteger aprobados = new AtomicInteger();
            enParalelo(10, 1, () -> {
                if (retirar(cuentas, "luis", 300)) {
                    aprobados.incrementAndGet();
                }
            });
            System.out.printf(ES, "   10 retiros de 300 sobre 1.000: aprobados %d, saldo final %,d%n", aprobados.get(), saldo(cuentas, "luis"));

            System.out.println("3. Transferencia: dos actualizaciones seguidas, y el proceso cae entre ellas");
            abrir(cuentas, "eva", 100_000);
            abrir(cuentas, "juan", 0);
            try {
                transferir(cuentas, "eva", "juan", 30_000, true);
            } catch (IllegalStateException caida) {
                System.out.println("   " + caida.getMessage());
            }
            System.out.printf(ES, "   eva %,d + juan %,d = %,d (antes había 100.000)%n",
                    saldo(cuentas, "eva"), saldo(cuentas, "juan"), saldo(cuentas, "eva") + saldo(cuentas, "juan"));
        }
    }

    private static void depositar(MongoCollection<Document> cuentas, String id, long valor) {
        Document cuenta = cuentas.find(eq("_id", id)).first();
        long nuevo = cuenta.getLong("saldo") + valor;                     // entre esta lectura…
        cuentas.replaceOne(eq("_id", id), cuenta.append("saldo", nuevo)); // …y esta escritura, otro pudo escribir
    }

    private static boolean retirar(MongoCollection<Document> cuentas, String id, long valor) {
        if (saldo(cuentas, id) < valor) {
            return false;                                                 // la regla se comprueba con un dato que envejece
        }
        consultarAntifraude();
        cuentas.updateOne(eq("_id", id), Updates.inc("saldo", -valor));   // $inc es atómico, pero no comprueba nada
        return true;
    }

    /** Lo que hace el código real entre comprobar y escribir: validar, registrar, preguntar a otro servicio. */
    private static void consultarAntifraude() {
        try {
            Thread.sleep(5);
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
        }
    }

    private static void transferir(MongoCollection<Document> cuentas, String origen, String destino, long valor, boolean caerse) {
        cuentas.updateOne(eq("_id", origen), Updates.inc("saldo", -valor));
        if (caerse) {
            throw new IllegalStateException("se cayó el proceso después de descontar y antes de abonar");
        }
        cuentas.updateOne(eq("_id", destino), Updates.inc("saldo", valor));
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
