import static com.mongodb.client.model.Filters.eq;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.bson.Document;

/**
 * El mismo banco, modelado a partir de sus consultas:
 *   "saldo de una cuenta"        -> un documento pequeño en cuentas, que incrusta lo que se lee con él (el titular)
 *   "últimos movimientos"        -> una colección aparte, que referencia la cuenta, con un índice para esa consulta
 */
public class Main {

    private static final long[] HITOS = {1_000, 10_000, 50_000, 100_000, 150_000, 200_000};
    private static final int LOTE = 10_000;
    private static final Locale ES = Locale.forLanguageTag("es-CO");

    public static void main(String[] args) {
        try (MongoClient cliente = MongoClients.create("mongodb://localhost:27017/?replicaSet=rs0")) {
            MongoDatabase base = cliente.getDatabase("nosql01_despues");
            base.drop();
            MongoCollection<Document> cuentas = base.getCollection("cuentas");
            MongoCollection<Document> movimientos = base.getCollection("movimientos");

            // El índice existe por una consulta concreta: los movimientos de una cuenta, del más reciente al más antiguo.
            movimientos.createIndex(Indexes.compoundIndex(Indexes.ascending("cuentaId"), Indexes.descending("fecha")));

            // Incrustado: el titular se lee siempre con la cuenta, cambia poco y su tamaño está acotado.
            cuentas.insertOne(new Document("_id", "cuenta-1")
                    .append("titular", new Document("nombre", "Ana").append("documento", "CC 1020304050"))
                    .append("saldo", 0L));

            System.out.println("movimientos | tamaño de la cuenta | leer el saldo | últimos 10");
            long registrados = 0;
            for (long hito : HITOS) {
                while (registrados < hito) {
                    int cuantos = (int) Math.min(LOTE, hito - registrados);
                    registrarMovimientos(cuentas, movimientos, registrados, cuantos);
                    registrados += cuantos;
                }
                System.out.printf(ES, "%,11d | %,16d B | %,10.1f ms | %,7.1f ms%n",
                        registrados, tamano(cuentas), milisegundos(() -> saldo(cuentas)),
                        milisegundos(() -> ultimos10(movimientos)));
            }
            System.out.printf(ES, "Saldo según la cuenta: %,d centavos%n", saldo(cuentas));
            System.out.printf(ES, "Movimientos guardados: %,d%n", movimientos.countDocuments(eq("cuentaId", "cuenta-1")));
            System.out.println("El más reciente: " + ultimos10(movimientos).get(0).getString("referencia"));
        }
    }

    /**
     * Los movimientos van a su colección y el saldo se actualiza aparte.
     * Son dos escrituras: si el proceso cae entre una y otra, no cuadran. La sesión 03 lo resuelve.
     */
    private static void registrarMovimientos(MongoCollection<Document> cuentas, MongoCollection<Document> movimientos,
                                             long desde, int cuantos) {
        List<Document> nuevos = new ArrayList<>(cuantos);
        for (int i = 0; i < cuantos; i++) {
            nuevos.add(new Document("cuentaId", "cuenta-1")
                    .append("tipo", "DEPOSITO")
                    .append("valor", 1_000L)
                    .append("fecha", new Date(1_763_200_000_000L + (desde + i) * 1_000))
                    .append("referencia", "consignación número " + (desde + i)));
        }
        movimientos.insertMany(nuevos);
        cuentas.updateOne(eq("_id", "cuenta-1"), Updates.inc("saldo", 1_000L * cuantos));
    }

    private static long saldo(MongoCollection<Document> cuentas) {
        Document cuenta = cuentas.find(eq("_id", "cuenta-1")).first();
        return cuenta == null ? 0 : cuenta.getLong("saldo");
    }

    /** La base ordena y corta: solo viajan diez documentos, y los encuentra por el índice. */
    private static List<Document> ultimos10(MongoCollection<Document> movimientos) {
        return movimientos.find(eq("cuentaId", "cuenta-1")).sort(Sorts.descending("fecha")).limit(10).into(new ArrayList<>());
    }

    private static int tamano(MongoCollection<Document> cuentas) {
        Document resultado = cuentas.aggregate(List.of(
                new Document("$match", new Document("_id", "cuenta-1")),
                new Document("$project", new Document("bytes", new Document("$bsonSize", "$$ROOT"))))).first();
        return resultado == null ? 0 : resultado.getInteger("bytes");
    }

    private static double milisegundos(Runnable lectura) {
        lectura.run();
        long inicio = System.nanoTime();
        for (int i = 0; i < 10; i++) {
            lectura.run();
        }
        return (System.nanoTime() - inicio) / 10 / 1_000_000.0;
    }
}
