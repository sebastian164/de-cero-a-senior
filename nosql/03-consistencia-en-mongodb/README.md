# 03 — Consistencia en MongoDB

> Requiere la [sesión 01](../01-modelar-por-consultas/) y las lecciones de [transacciones](../../java/nivel-08-bases-de-datos/03-transacciones-y-aislamiento/) y [concurrencia](../../java/nivel-08-bases-de-datos/04-concurrencia-y-bloqueos/) del Nivel 8. El [entorno](../entorno/) levanta MongoDB como replica set: sin eso, esta sesión no funciona.

## Idea

Dos cajeros y un libro de saldos en papel. Cada uno lee el saldo de Ana, suma en su cabeza lo que ella consignó y escribe el nuevo saldo encima. El segundo borra lo del primero sin saberlo.

Hay cuatro maneras de arreglarlo, de menos a más costosa. Pedirle al libro que sume él ("súmale 1.000"). Ponerle una condición a la escritura ("descuenta 300, solo si hay al menos 300"). Escribir solo si la página no ha cambiado desde que la leíste ("si sigue siendo la versión 7"). Y, cuando hay que tocar dos páginas que deben cambiar juntas, cerrar el libro mientras tanto: una transacción.

## Lo esencial

### Lo que MongoDB garantiza sin pedir nada

**Una escritura sobre un documento es atómica**, incluidos sus subdocumentos y arreglos. Los operadores (`$inc`, `$set`, `$push`…) y las condiciones del filtro se evalúan en el servidor, sobre el documento, sin que nadie se cuele en medio. Entre dos documentos no hay ninguna garantía, salvo que se pida una transacción.

Por eso la [sesión 01](../01-modelar-por-consultas/) importa también aquí: lo que tiene que cambiar junto, si cabe en un documento, no necesita transacción.

### Cuatro herramientas

| Herramienta | Cuándo | Cómo |
|---|---|---|
| Operador atómico | El cambio no depende de ninguna regla: sumar, agregar | `updateOne({_id}, {$inc: {saldo: 1000}})` |
| Actualización condicional | La regla se puede escribir como un filtro | `updateOne({_id, saldo: {$gte: 300}}, {$inc: {saldo: -300}})` y mirar si modificó algo |
| Bloqueo optimista con versión | La regla vive en Java, en el dominio, y no cabe en un filtro | Leer con `version`, decidir, escribir con `{_id, version: v}` y `$inc: {version: 1}`; si no coincidió, otro escribió: volver a leer |
| Transacción multidocumento | Varios documentos tienen que cambiar juntos o no cambiar | `session.withTransaction(...)`; requiere replica set |

Un operador atómico no hace atómica una decisión. Si el código comprueba el saldo con una lectura y descuenta con `$inc` en otra operación, la comprobación y el descuento están separados: es la "comprobación que envejece" del paso 2 del ejemplo.

### Transacciones: lo que hay que saber

- **Solo en un replica set** (o un clúster con fragmentación). Contra un `mongod` suelto, el servidor rechaza la orden con el código 20 (`Transaction numbers are only allowed on a replica set member or mongos`); el controlador 5.14.0 y `mongosh` sustituyen ese mensaje por `This MongoDB deployment does not support retryable writes. Please add retryWrites=false to your connection string.`, que es el que verás.
- **Aislamiento por instantánea.** Con `readConcern: snapshot`, todo lo que lee la transacción sale de la misma foto.
- **Conflictos inmediatos.** Si dos transacciones escriben el mismo documento, la segunda no espera como haría PostgreSQL con un bloqueo de fila: recibe un `WriteConflict` en el acto. El error lleva la etiqueta `TransientTransactionError`, que significa "repite la transacción entera y probablemente saldrá". `withTransaction` lo hace por ti, durante un máximo de 120 segundos.
- **Confirmación con resultado desconocido.** Si se corta la red al confirmar, el error lleva `UnknownTransactionCommitResult`: se puede repetir la confirmación sin riesgo. `withTransaction` también lo hace.
- **Cualquier error la aborta.** Una clave duplicada, por ejemplo, no se puede capturar y seguir: la transacción ya no sirve.
- **Son cortas.** Por defecto, el servidor aborta las que duran más de 60 segundos. Y cuestan más que una escritura simple: si casi todas tus escrituras necesitan una, revisa el modelo.

### Write concern, read concern y preferencia de lectura

| Opción | Valor | Qué significa |
|---|---|---|
| Write concern | `w: 1` | Confirmada cuando la tiene el primario. Si el primario cae antes de replicarla, se puede perder |
| | `w: "majority"` | Confirmada cuando la tiene la mayoría del replica set. Sobrevive a la caída del primario. Es el valor por defecto |
| | `j: true` | Además, escrita en el diario en disco |
| Read concern | `local` | Lo último que tiene el nodo, aunque pueda revertirse |
| | `majority` | Solo lo que ya confirmó la mayoría |
| | `snapshot` | En una transacción: una foto coherente de lo confirmado por la mayoría |
| Preferencia de lectura | `primary` | Leer siempre del primario |
| | `secondary`… | Leer de una réplica: menos carga en el primario, pero puedes no ver lo que acabas de escribir |

Con un replica set de un nodo, como el del entorno, "la mayoría" es ese nodo: aquí no se puede ver la diferencia entre `w: 1` y `w: "majority"`. La diferencia existe en cuanto hay tres nodos y uno cae, y es la diferencia entre "confirmado" y "confirmado y perdido".

## Ejemplo

Cuatro operaciones del banco sobre documentos `{_id, saldo}`. El retiro hace "otras cosas" durante 5 ms entre leer y escribir (validar, registrar, preguntar a un servicio antifraude), como cualquier código real: eso solo ensancha una ventana que existe igual sin la pausa.

### Antes

<!-- file: ejemplo/antes/src/Main.java -->
```java
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
```

```text
1. Depósitos: leer el documento, sumar en Java y reemplazarlo
   esperado 400.000, guardado 50.000
2. Retiros: comprobar el saldo en Java y luego descontar con $inc
   10 retiros de 300 sobre 1.000: aprobados 10, saldo final -2.000
3. Transferencia: dos actualizaciones seguidas, y el proceso cae entre ellas
   se cayó el proceso después de descontar y antes de abonar
   eva 70.000 + juan 0 = 70.000 (antes había 100.000)
```

### Análisis

| Paso | Lo que pasó |
|---|---|
| 1 | De 400 depósitos de 1.000 quedaron 50.000: se perdieron 350. Cada hilo lee el documento, suma en Java y lo reemplaza entero; el último que escribe borra lo de los demás. Es la actualización perdida del Nivel 8, ahora con documentos |
| 2 | Diez retiros de 300 aprobados sobre un saldo de 1.000: la cuenta quedó en -2.000. El `$inc` es atómico, pero la decisión se tomó antes, con un dato que ya no era cierto |
| 3 | Desaparecieron 30.000. Dos documentos, dos escrituras, nada que las una |

Con un solo usuario, las tres operaciones son correctas. Las pruebas unitarias pasan, la demostración funciona. El número exacto de depósitos perdidos cambia en cada ejecución; que se pierden, no.

### Después

<!-- file: ejemplo/despues/src/Main.java -->
```java
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
```

```text
1. Depósitos: la suma la hace el servidor, dentro del documento
   esperado 400.000, guardado 400.000
2a. Retiros: la regla va en el filtro de la actualización
   10 retiros de 300 sobre 1.000: aprobados 3, saldo final 100
2b. Retiros: la regla se decide en Java y se guarda solo si nadie cambió la versión
   10 retiros de 300 sobre 1.000: aprobados 3, saldo final 100, conflictos detectados y reintentados 24
3. Transferencia en una transacción, y el proceso cae entre las dos actualizaciones
   se cayó el proceso después de descontar y antes de abonar
   eva 100.000 + juan 0 = 100.000 (antes había 100.000)
4. 200 transferencias a la vez entre eva y juan, en ambos sentidos, cada una en su transacción
   eva 94.000 + juan 6.000 = 100.000; intentos 364 para 200 transferencias (6 rechazadas por saldo)
```

| Paso | Herramienta | Por qué funciona |
|---|---|---|
| 1 | `$inc` | La suma la hace el servidor sobre el documento. No hay lectura en Java que pueda quedar vieja |
| 2a | Condición en el filtro | Comprobar y descontar son la misma operación. Si el saldo no alcanza, el filtro no encuentra el documento y `modifiedCount` es 0 |
| 2b | Versión | La regla se decide en Java, como haría el dominio del banco. La escritura solo ocurre si la versión no cambió; si cambió, se vuelve a leer y a decidir. Los 24 conflictos son el sistema funcionando, no un fallo |
| 3 | Transacción | El débito no se confirma hasta que el abono también esté. Al fallar, `withTransaction` aborta y no queda nada |
| 4 | Transacción con conflictos | 364 intentos para 200 transferencias: unas 160 veces dos transacciones tocaron la misma cuenta a la vez, una recibió `WriteConflict` y `withTransaction` la repitió. El total sigue en 100.000 |

La 2a y la 2b dan el mismo resultado. La 2a es más barata y no necesita reintentos; la 2b sirve cuando la regla no cabe en un filtro (el tope diario de la cuenta del banco, que depende del día del último envío, es un buen ejemplo). La [sesión 04](../04-mongodb-en-el-banco-hexagonal/) usa la versión porque las reglas del banco están en su dominio.

## Errores comunes

- Leer, modificar en Java y reemplazar el documento entero (`replaceOne`, `save`) sin versión.
- Comprobar una regla con una lectura y escribir con otra operación.
- Usar transacciones para todo, en lugar de modelar para que lo que cambia junto esté junto.
- Abrir una transacción y llamar dentro a otro servicio: más conflictos y más posibilidades de superar el límite de tiempo.
- Capturar el `WriteConflict` y darlo por fallo definitivo en vez de repetir la transacción.
- Probar las transacciones contra un `mongod` suelto en las pruebas y contra un replica set en producción.
- Leer de una réplica justo después de escribir y sorprenderse de no ver el cambio.
- Bajar el write concern a `w: 1` para ganar milisegundos sin decidir qué pasa si el primario cae.

## Taller

**Objetivo:** que tu servicio de pedidos no venda lo que no tiene ni pierda cambios de estado, usando en cada caso la herramienta más barata que sea correcta.

Sobre tu modelo de documentos del [taller 01](../01-modelar-por-consultas/), con el controlador de Java.

1. **Existencias.** Un producto tiene 5 unidades y 20 hilos intentan comprar una a la vez. Escríbelo primero con lectura y escritura separadas y demuestra la sobreventa. Corrígelo con una actualización condicional y demuestra que se venden exactamente 5. Repite diez veces.
2. **Estado del pedido.** Las transiciones (`PENDIENTE` → `PAGADO` → `ENVIADO`, y `CANCELADO` solo desde `PENDIENTE`) se deciden en Java. Lanza a la vez "pagar" y "cancelar" sobre el mismo pedido. Demuestra que, sin versión, puede quedar pagado y cancelado según quién escriba último. Corrígelo con bloqueo optimista.
3. **Crear pedido.** Crear el pedido y descontar las existencias de sus tres productos tiene que ocurrir junto. Hazlo con una transacción y provoca un fallo al descontar el tercero: ni pedido ni descuentos.
4. **Conflictos.** Lanza 200 creaciones de pedido concurrentes sobre los mismos dos productos y cuenta cuántas veces se repitió la transacción. ¿Cuántas terminaron, cuántas se rechazaron por falta de existencias?
5. **Coste.** Mide la latencia media y el p95 de descontar una unidad con actualización condicional y con una transacción de un solo documento. ¿Cuánto cuesta la transacción cuando no hace falta?
6. **Opcional.** Levanta un replica set de tres nodos, escribe con `w: 1`, detén el primario antes de que replique y comprueba qué pasa con esa escritura. Repite con `w: "majority"`. Para impedir que replique, aísla a los secundarios de la red (`docker network disconnect`); un `docker pause` no sirve, porque el sistema operativo sigue entregando al secundario congelado lo que el primario le envía y lo aplica al despertar.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| Nunca se venden más existencias de las que hay | Paso 1, diez ejecuciones |
| Un pedido nunca termina en un estado imposible | Paso 2 |
| Un fallo a mitad de "crear pedido" no deja nada | Paso 3 |
| Cada operación usa la herramienta más barata que es correcta, y está escrito por qué | `ANALISIS.md` |
| Los reintentos por conflicto están medidos | Paso 4 |

**Entrega:** rama `taller/nosql-03`, con los programas, sus salidas y `ANALISIS.md` en `taller/solucion/`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

Con Spring Data MongoDB, un campo anotado con `@Version` da el bloqueo optimista de esta sesión: el repositorio añade la versión al filtro y lanza `OptimisticLockingFailureException` si no coincide. `@Transactional` funciona con MongoDB, pero solo si declaras un `MongoTransactionManager`: Spring Boot no lo crea por su cuenta, y sin él la anotación no hace nada con MongoDB y no avisa.

La mayoría de los equipos que llegan a MongoDB desde una base relacional abusan de las transacciones al principio, y la mayoría de los que llegan sin pasar por una relacional las ignoran. La pregunta útil es siempre la misma: ¿qué tiene que cambiar junto, y cabe en un documento?

## Preguntas senior

1. ¿Qué garantiza MongoDB sobre un documento sin pedir una transacción, y qué no garantiza entre dos?
2. ¿Por qué `$inc` no basta para un retiro con saldo mínimo? ¿Qué lo hace correcto?
3. ¿Cuándo eliges una actualización condicional y cuándo bloqueo optimista con versión?
4. ¿Qué es un `TransientTransactionError` y qué hace tu código con él?
5. ¿En qué se diferencia un conflicto de escritura en MongoDB de un bloqueo de fila en PostgreSQL, y qué consecuencia tiene bajo mucha concurrencia?
6. ¿Qué arriesgas con `w: 1`? ¿Y leyendo de una réplica?
