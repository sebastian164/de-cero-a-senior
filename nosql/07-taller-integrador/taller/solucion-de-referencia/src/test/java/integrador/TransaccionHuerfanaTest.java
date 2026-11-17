package integrador;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import comun.Entorno;
import comun.Hilos;
import comun.Salida;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.Test;

/**
 * Qué pasa con los documentos que tocaba una instancia muerta a mitad de transacción. MongoDB conserva su transacción abierta hasta
 * {@code transactionLifetimeLimitSeconds} (60 s por defecto): mientras tanto, cualquier otra transacción que escriba esos documentos
 * choca con un WriteConflict. Se mide con el valor por defecto y con 5 s, y se compara con lo que ve el servicio (ConflictoDeConcurrencia).
 */
class TransaccionHuerfanaTest {
    static final String R = "transaccion-huerfana.txt";

    static long esperaTrasMatar(MongoClient admin, String url, long limiteSegundos) throws Exception {
        String bd = "t07h_" + UUID.randomUUID().toString().substring(0, 6);
        admin.getDatabase("admin").runCommand(new Document("setParameter", 1).append("transactionLifetimeLimitSeconds", limiteSegundos));
        admin.getDatabase(bd).getCollection("productos").insertOne(new Document("_id", "P").append("existencias", 10));
        Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java", "-Dorg.slf4j.simpleLogger.defaultLogLevel=error", "-cp", System.getProperty("java.class.path"),
                "integrador.HuerfanaProceso", url, bd).redirectErrorStream(true).start();
        try (var in = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            assertEquals("ABIERTA", in.readLine());
        }
        p.destroyForcibly();
        p.waitFor();                                                      // kill -9 con la transacción abierta
        long t0 = System.currentTimeMillis();
        int conflictos = 0;
        while (true) {
            try (var sesion = admin.startSession()) {
                sesion.startTransaction();
                admin.getDatabase(bd).getCollection("productos").updateOne(sesion, Filters.eq("_id", "P"), Updates.inc("existencias", -1));
                sesion.commitTransaction();
                break;
            } catch (MongoException e) {
                assertTrue(e.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL), e.toString());   // lo que el servicio traduce a ConflictoDeConcurrencia
                conflictos++;
                assertTrue(System.currentTimeMillis() - t0 < 150_000);
                Hilos.dormir(250);
            }
        }
        long ms = System.currentTimeMillis() - t0;
        Salida.linea(R, "transactionLifetimeLimitSeconds=%d: una transacción nueva sobre el mismo documento falló %d veces con WriteConflict y pudo escribir %d ms después del kill -9".formatted(limiteSegundos, conflictos, ms));
        admin.getDatabase(bd).drop();
        return ms;
    }

    @Test
    void laEsperaTrasMatarUnProcesoEnPlenaTransaccionLaAcotaTransactionLifetimeLimitSeconds() throws Exception {
        Salida.limpiar(R);
        String url = Entorno.mongoUrl();
        try (MongoClient admin = Entorno.mongo()) {
            long con5 = esperaTrasMatar(admin, url, 5);
            long con15 = esperaTrasMatar(admin, url, 15);
            long conDefecto = esperaTrasMatar(admin, url, 60);
            Salida.linea(R, ("Conclusión: la espera es 1,5 veces el límite (5 s -> %d ms, 15 s -> %d ms, 60 s -> %d ms): el límite más el intervalo del barrido que aborta las transacciones caducadas, que corre cada mitad del límite. "
                    + "El compose del integrador fija 15 s: las transacciones del servicio duran milisegundos, así que 15 s deja un margen de tres órdenes de magnitud y acota a ~22 s lo que tardan en recuperarse los productos que tocaba una instancia muerta "
                    + "(con el valor por defecto serían ~90 s de 409/503). Más bajo es peligroso: abortaría transacciones lentas pero legítimas (una pausa de GC, una red lenta).").formatted(con5, con15, conDefecto));
            assertTrue(con5 < con15 && con15 < conDefecto);
            assertTrue(con5 >= 2_000 && con5 <= 20_000, "con 5 s esperó " + con5);
            assertTrue(conDefecto >= 30_000, "con el valor por defecto esperó " + conDefecto);
        }
    }
}
