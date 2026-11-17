package t03;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.mongodb.MongoException;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import comun.Salida;
import org.bson.Document;
import org.junit.jupiter.api.Test;

/** Comprueba la afirmación del README del entorno: sin replica set la primera transacción falla (con el controlador 5.14.0, antes de llegar al servidor). Solo corre con -Dstandalone.url=... */
class SinReplicaSetTest {
    @Test
    void laPrimeraTransaccionFallaContraUnMongodSuelto() {
        String url = System.getProperty("standalone.url");
        assumeTrue(url != null, "sin -Dstandalone.url no se ejecuta");
        try (MongoClient c = MongoClients.create(url); ClientSession s = c.startSession()) {
            s.startTransaction();
            MongoException e = assertThrows(MongoException.class, () -> c.getDatabase("x").getCollection("c").insertOne(s, new Document("a", 1)));
            Salida.linea("sin-replica-set.txt", e.getClass().getSimpleName() + ": " + e.getMessage());
            assertTrue(e.getMessage().contains("does not support retryable writes"), e.getMessage());   // el servidor rechaza la orden (código 20, IllegalOperation) y el controlador sustituye su mensaje por este
        }
    }
}
