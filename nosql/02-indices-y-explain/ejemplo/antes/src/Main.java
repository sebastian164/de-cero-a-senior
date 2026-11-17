import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Filters.lt;

import com.mongodb.ExplainVerbosity;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import org.bson.Document;
import org.bson.conversions.Bson;

/**
 * 200.000 movimientos de 1.000 cuentas. Las consultas responden bien y rápido en el portátil del desarrollador.
 * Hay un índice por fecha "porque siempre se ordena por fecha", y el extracto pagina con skip.
 */
public class Main {

    private static final int CUENTAS = 1_000;
    private static final int MOVIMIENTOS = 200_000;
    private static final long INICIO = 1_763_200_000_000L;               // 15 de noviembre de 2025
    private static final Locale ES = Locale.forLanguageTag("es-CO");

    public static void main(String[] args) {
        try (MongoClient cliente = MongoClients.create("mongodb://localhost:27017/?replicaSet=rs0")) {
            MongoDatabase base = cliente.getDatabase("nosql02_antes");
            base.drop();
            MongoCollection<Document> movimientos = base.getCollection("movimientos");
            cargar(movimientos);
            movimientos.createIndex(Indexes.descending("fecha"));

            System.out.println("consulta                         | plan                     |  claves | documentos | devueltos |  ms");

            Bson deLaCuenta = eq("cuentaId", "cuenta-500");
            Bson recientes = Sorts.descending("fecha");
            mostrar("extracto: 20 más recientes", movimientos.find(deLaCuenta).sort(recientes).limit(20));

            Bson deUnPeriodo = and(deLaCuenta, gte("fecha", new Date(INICIO + 100_000L * 60_000)),
                    lt("fecha", new Date(INICIO + 150_000L * 60_000)));
            mostrar("extracto de un rango de fechas", movimientos.find(deUnPeriodo).sort(recientes));

            mostrar("página 9 (skip 160)", movimientos.find(deLaCuenta).sort(recientes).skip(160).limit(20));

            System.out.println("Índices: " + nombresDeIndices(movimientos));
        }
    }

    /** Cada cuenta tiene 200 movimientos, uno por minuto, repartidos al azar entre todas (con semilla fija). */
    private static void cargar(MongoCollection<Document> movimientos) {
        Random azar = new Random(42);
        List<Document> lote = new ArrayList<>();
        for (int i = 0; i < MOVIMIENTOS; i++) {
            lote.add(new Document("cuentaId", "cuenta-" + azar.nextInt(CUENTAS))
                    .append("tipo", azar.nextBoolean() ? "DEPOSITO" : "RETIRO")
                    .append("valor", 1_000L + azar.nextInt(100_000))
                    .append("fecha", new Date(INICIO + i * 60_000L)));
            if (lote.size() == 10_000) {
                movimientos.insertMany(lote);
                lote.clear();
            }
        }
    }

    private static void mostrar(String nombre, FindIterable<Document> consulta) {
        Document plan = consulta.explain(ExplainVerbosity.EXECUTION_STATS);
        Document estadisticas = plan.get("executionStats", Document.class);
        Document ganador = plan.get("queryPlanner", Document.class).get("winningPlan", Document.class);
        System.out.printf(ES, "%-32s | %-24s | %,7d | %,10d | %,9d | %3d%n", nombre, etapas(ganador),
                estadisticas.getInteger("totalKeysExamined"), estadisticas.getInteger("totalDocsExamined"),
                estadisticas.getInteger("nReturned"), estadisticas.getInteger("executionTimeMillis"));
    }

    /** Las etapas del plan ganador, de la última a la primera: LIMIT <- FETCH <- IXSCAN. */
    private static String etapas(Document plan) {
        Set<String> encontradas = new LinkedHashSet<>();
        recorrer(plan.containsKey("queryPlan") ? plan.get("queryPlan", Document.class) : plan, encontradas);
        return String.join("<", encontradas);
    }

    private static void recorrer(Document etapa, Set<String> encontradas) {
        encontradas.add(etapa.getString("stage"));
        if (etapa.containsKey("inputStage")) {
            recorrer(etapa.get("inputStage", Document.class), encontradas);
        }
        for (Document entrada : etapa.getList("inputStages", Document.class, List.of())) {
            recorrer(entrada, encontradas);
        }
    }

    private static List<String> nombresDeIndices(MongoCollection<Document> movimientos) {
        return movimientos.listIndexes().map(indice -> indice.getString("name")).into(new ArrayList<>());
    }
}
