import static com.mongodb.client.model.Filters.eq;

import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Updates;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.bson.Document;

/** Cada cuenta es un documento y lleva dentro todos sus movimientos. "Es lo natural: el extracto es de la cuenta". */
public class Main {

    private static final long[] HITOS = {1_000, 10_000, 50_000, 100_000, 150_000, 200_000};
    private static final int LOTE = 10_000;
    private static final Locale ES = Locale.forLanguageTag("es-CO");

    public static void main(String[] args) {
        try (MongoClient cliente = MongoClients.create("mongodb://localhost:27017/?replicaSet=rs0")) {
            MongoDatabase base = cliente.getDatabase("nosql01_antes");
            base.drop();
            MongoCollection<Document> cuentas = base.getCollection("cuentas");

            cuentas.insertOne(new Document("_id", "cuenta-1")
                    .append("titular", "Ana")
                    .append("saldo", 0L)
                    .append("movimientos", new ArrayList<Document>()));

            System.out.println("movimientos | tamaño del documento | leer el saldo | últimos 10");
            long registrados = 0;
            for (long hito : HITOS) {
                try {
                    while (registrados < hito) {
                        int cuantos = (int) Math.min(LOTE, hito - registrados);
                        registrarMovimientos(cuentas, registrados, cuantos);
                        registrados += cuantos;
                    }
                } catch (MongoException error) {
                    System.out.printf(ES, "%,11d | la cuenta ya no acepta movimientos: código %d%n", registrados, error.getCode());
                    System.out.println("            " + error.getMessage().lines().findFirst().orElse(""));
                    break;
                }
                System.out.printf(ES, "%,11d | %,17d B | %,10.1f ms | %,7.1f ms%n",
                        registrados, tamano(cuentas), milisegundos(() -> saldo(cuentas)), milisegundos(() -> ultimos10(cuentas)));
            }
            System.out.printf(ES, "Saldo según la cuenta: %,d centavos%n", saldo(cuentas));
        }
    }

    /** Cada movimiento se agrega al final del arreglo y se suma al saldo, en una sola actualización. */
    private static void registrarMovimientos(MongoCollection<Document> cuentas, long desde, int cuantos) {
        List<Document> nuevos = new ArrayList<>(cuantos);
        for (int i = 0; i < cuantos; i++) {
            nuevos.add(new Document("tipo", "DEPOSITO")
                    .append("valor", 1_000L)
                    .append("fecha", new Date(1_763_200_000_000L + (desde + i) * 1_000))
                    .append("referencia", "consignación número " + (desde + i)));
        }
        cuentas.updateOne(eq("_id", "cuenta-1"),
                Updates.combine(Updates.pushEach("movimientos", nuevos), Updates.inc("saldo", 1_000L * cuantos)));
    }

    /** Para saber el saldo se trae la cuenta. Y la cuenta lo trae todo. */
    private static long saldo(MongoCollection<Document> cuentas) {
        Document cuenta = cuentas.find(eq("_id", "cuenta-1")).first();
        return cuenta == null ? 0 : cuenta.getLong("saldo");
    }

    /** El extracto de la pantalla principal: los diez más recientes. */
    private static List<Document> ultimos10(MongoCollection<Document> cuentas) {
        Document cuenta = cuentas.find(eq("_id", "cuenta-1")).first();
        List<Document> todos = cuenta == null ? List.of() : cuenta.getList("movimientos", Document.class);
        return todos.subList(Math.max(0, todos.size() - 10), todos.size());
    }

    private static int tamano(MongoCollection<Document> cuentas) {
        Document resultado = cuentas.aggregate(List.of(
                new Document("$match", new Document("_id", "cuenta-1")),
                new Document("$project", new Document("bytes", new Document("$bsonSize", "$$ROOT"))))).first();
        return resultado == null ? 0 : resultado.getInteger("bytes");
    }

    /** El promedio de 10 ejecuciones, para que una sola lectura lenta o rápida no engañe. */
    private static double milisegundos(Runnable lectura) {
        lectura.run();                                           // la primera calienta conexiones y caché
        long inicio = System.nanoTime();
        for (int i = 0; i < 10; i++) {
            lectura.run();
        }
        return (System.nanoTime() - inicio) / 10 / 1_000_000.0;
    }
}
