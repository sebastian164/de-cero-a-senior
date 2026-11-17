package integrador;

import com.mongodb.client.MongoClients;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

/** Abre una transacción, escribe un documento sin confirmar y se queda esperando: la prueba lo mata con kill -9. */
public final class HuerfanaProceso {
    public static void main(String[] a) throws Exception {
        try (var cliente = MongoClients.create(a[0]); var sesion = cliente.startSession()) {
            sesion.startTransaction();
            cliente.getDatabase(a[1]).getCollection("productos").updateOne(sesion, Filters.eq("_id", "P"), Updates.inc("existencias", -1));
            System.out.println("ABIERTA");
            System.out.flush();
            Thread.sleep(600_000);
        }
    }
}
