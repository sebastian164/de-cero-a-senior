# 04 — MongoDB en el banco hexagonal

> Requiere las sesiones [01](../01-modelar-por-consultas/) a [03](../03-consistencia-en-mongodb/) y el módulo de [arquitectura hexagonal](../../hexagonal/), sobre todo [transacciones](../../hexagonal/04-transacciones/), [concurrencia](../../hexagonal/05-concurrencia-y-aislamiento/) y [pruebas y límites](../../hexagonal/08-pruebas-y-limites/). El ejemplo se compila **junto con el núcleo del banco**, sin modificarlo.

## Idea

Un enchufe y un electrodoméstico. La nevera no sabe si la corriente viene de la red, de una planta o de paneles solares: sabe que el enchufe da 120 voltios. Cambiar de proveedor no obliga a cambiar la nevera, siempre que el nuevo cumpla lo que el enchufe promete. Y si no lo cumple, se nota en la nevera, no en la factura.

El banco es la nevera; los puertos de salida son el enchufe; MongoDB es un proveedor nuevo. La sesión trata de qué significa "cumplir lo que el enchufe promete" y de cómo se comprueba antes de enchufar.

## Lo esencial

### Dónde entra MongoDB

```
caso de uso ──► puertos de salida ──► adaptadores de MongoDB ──► colecciones
                 RepositorioDeCuentas      CuentasMongo             cuentas
                 RegistroDeMovimientos     MovimientosMongo         movimientos
                 OrdenesProcesadas         OrdenesMongo             ordenes_procesadas
                 PublicadorDeEventos       BandejaDeSalidaMongo     bandeja_de_salida
                 UnidadDeTrabajo           UnidadDeTrabajoMongo     (sesión y transacción)
```

| Pieza | Qué sabe de MongoDB |
|---|---|
| Dominio (`Cuenta`, `Dinero`…) | Nada |
| Aplicación (`ServicioDeCuentas`) | Nada. Pide "esto va junto" y "si hay conflicto, reintento" |
| `UnidadDeTrabajoMongo` | Abre la sesión, inicia, confirma o aborta la transacción, y traduce sus errores |
| Los repositorios | Traducen entre objetos del dominio y documentos, y usan la sesión de la transacción en curso |
| La raíz de composición (`Main`) | Crea el cliente, prepara colecciones e índices y conecta cada adaptador a su puerto |

Es la misma forma que el adaptador JDBC de la [sesión 06 de HikariCP](../../hikaricp/06-el-pool-en-un-servicio-hexagonal/): allí la unidad de trabajo tomaba una conexión del pool; aquí abre una `ClientSession`. En los dos casos, los repositorios no abren nada: usan lo que la unidad de trabajo tiene abierto en ese hilo.

### Lo que el puerto promete

`RepositorioDeCuentas` no dice "guarda en algún sitio". Dice, en su contrato del módulo hexagonal:

| Cláusula | En MongoDB se cumple con |
|---|---|
| Lo guardado se recupera igual | La traducción documento ↔ cuenta, en un solo sitio |
| Buscar lo que no existe devuelve vacío | `find(...).first()` es `null` |
| Los identificadores no se repiten | Un `ObjectId` generado en el cliente |
| Lo que se entrega es una copia | Cada lectura construye una `Cuenta` nueva |
| Guardar con una versión vieja falla con conflicto | `updateOne({_id, version: v}, …)` y `ConflictoDeConcurrencia` si no coincidió (sesión 03) |

Y `UnidadDeTrabajo` promete "todo o nada". Con MongoDB eso es una transacción multidocumento, porque una transferencia toca dos cuentas, dos movimientos, una orden y un evento.

### Traducir los errores

La aplicación ya sabe reintentar cuando recibe `ConflictoDeConcurrencia`. MongoDB tiene dos formas de decir "otro escribió lo mismo": la versión que no coincide (la detecta el adaptador) y el `WriteConflict` entre transacciones, que llega con la etiqueta `TransientTransactionError`. El adaptador traduce las dos a `ConflictoDeConcurrencia`. Así el núcleo no conoce las etiquetas de MongoDB, y el reintento sigue siendo una decisión de la aplicación.

### Decisiones pequeñas que importan

- **El identificador.** Un contador en un documento (`$inc` en `secuencias`) daría identificadores bonitos y un documento que todas las aperturas de cuenta escriben a la vez: un punto caliente de conflictos. Un `ObjectId` se genera sin ir a la base.
- **Las colecciones y los índices se crean al arrancar**, fuera de cualquier transacción, y crearlos dos veces no hace nada.
- **Las órdenes procesadas se buscan antes de insertarlas.** Dentro de una transacción, un error de clave duplicada la aborta entera: no se puede capturar y responder "repetida".
- **Los eventos van a una bandeja de salida** en la misma transacción. Es el outbox del [módulo de Kafka](../../kafka-y-sagas/kafka/07-outbox-transaccional/), con MongoDB.

## Ejemplo

Los dos proyectos compilan `hexagonal/proyecto/src` y `hexagonal/proyecto/pruebas` desde donde están, sin copiarlos; así pueden usar `ContratoDelRepositorio`, `RepositorioQueFalla` y `Comprobar` tal cual:

<!-- file: ejemplo/despues/pom.xml -->
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>curso.nosql</groupId>
        <artifactId>nosql</artifactId>
        <version>1.0.0</version>
        <relativePath>../../../pom.xml</relativePath>
    </parent>

    <artifactId>nosql-04-despues</artifactId>

    <dependencies>
        <dependency>
            <groupId>org.mongodb</groupId>
            <artifactId>mongodb-driver-sync</artifactId>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-simple</artifactId>
            <scope>runtime</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- El banco no se copia: su núcleo y sus pruebas se compilan desde hexagonal/proyecto, sin tocarlos. -->
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>build-helper-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <id>banco</id>
                        <phase>generate-sources</phase>
                        <goals>
                            <goal>add-source</goal>
                        </goals>
                        <configuration>
                            <sources>
                                <source>../../../../hexagonal/proyecto/src</source>
                                <source>../../../../hexagonal/proyecto/pruebas</source>
                            </sources>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

Los adaptadores están en un paquete `banco.adaptadores.salida.mongo`. La regla `salida/` del `.gitignore` de la raíz ignoraría esa carpeta; el [`.gitignore`](../.gitignore) del módulo la devuelve al repositorio.

Desde `ejemplo/antes` y desde `ejemplo/despues`:

```bash
mvn -q compile exec:java
```

Cada `Main` ejecuta la misma batería: el contrato del repositorio, una transferencia que falla a mitad, la misma orden enviada ocho veces a la vez y 200 transferencias concurrentes entre cuatro cuentas.

### Antes

Un primer adaptador, escrito con lo que parece razonable: guardar es reemplazar, y como "en MongoDB cada escritura ya es atómica", la unidad de trabajo no hace nada.

<!-- file: ejemplo/antes/src/banco/adaptadores/salida/mongo/CuentasMongo.java -->
```java
package banco.adaptadores.salida.mongo;

import static com.mongodb.client.model.Filters.eq;

import banco.aplicacion.puertos.salida.RepositorioDeCuentas;
import banco.dominio.Cuenta;
import banco.dominio.Dinero;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import java.time.LocalDate;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.ObjectId;

/** "Guardar es reemplazar el documento; si no existe, se crea". Corto, claro, y la última escritura gana. */
public final class CuentasMongo implements RepositorioDeCuentas {

    private final MongoCollection<Document> cuentas;

    public CuentasMongo(MongoDatabase base) {
        this.cuentas = base.getCollection("cuentas");
    }

    @Override
    public String siguienteId() {
        return "cuenta-" + new ObjectId().toHexString();
    }

    @Override
    public void guardar(Cuenta cuenta) {
        Document documento = new Document("_id", cuenta.id())
                .append("titular", cuenta.titular())
                .append("saldoCentavos", cuenta.saldo().centavos())
                .append("enviadoHoyCentavos", cuenta.enviadoHoy().centavos())
                .append("diaDelTope", cuenta.diaDelTope().toString())
                .append("version", cuenta.version() + 1);
        cuentas.replaceOne(eq("_id", cuenta.id()), documento, new ReplaceOptions().upsert(true));
    }

    @Override
    public Optional<Cuenta> buscar(String id) {
        return Optional.ofNullable(cuentas.find(eq("_id", id)).first())
                .map(documento -> Cuenta.existente(id, documento.getString("titular"),
                        new Dinero(documento.getLong("saldoCentavos")), new Dinero(documento.getLong("enviadoHoyCentavos")),
                        LocalDate.parse(documento.getString("diaDelTope")), documento.getLong("version")));
    }
}
```

<!-- file: ejemplo/antes/src/banco/adaptadores/salida/mongo/UnidadDeTrabajoMongo.java -->
```java
package banco.adaptadores.salida.mongo;

import banco.aplicacion.puertos.salida.UnidadDeTrabajo;
import java.util.function.Supplier;

/** "En MongoDB cada escritura ya es atómica, así que aquí no hay nada que hacer". */
public final class UnidadDeTrabajoMongo implements UnidadDeTrabajo {

    @Override
    public <T> T ejecutar(Supplier<T> trabajo) {
        return trabajo.get();
    }
}
```

<!-- file: ejemplo/antes/src/banco/adaptadores/salida/mongo/MovimientosMongo.java -->
```java
package banco.adaptadores.salida.mongo;

import static com.mongodb.client.model.Filters.eq;

import banco.aplicacion.puertos.salida.RegistroDeMovimientos;
import banco.dominio.Dinero;
import banco.dominio.Movimiento;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Sorts;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bson.Document;

public final class MovimientosMongo implements RegistroDeMovimientos {

    private final MongoCollection<Document> movimientos;

    public MovimientosMongo(MongoDatabase base) {
        this.movimientos = base.getCollection("movimientos");
    }

    @Override
    public void registrar(Movimiento movimiento) {
        movimientos.insertOne(new Document("cuentaId", movimiento.cuentaId())
                .append("tipo", movimiento.tipo().name())
                .append("valorCentavos", movimiento.valor().centavos())
                .append("fecha", Date.from(movimiento.fecha()))
                .append("referencia", movimiento.referencia()));
    }

    @Override
    public List<Movimiento> de(String cuentaId) {
        List<Movimiento> resultado = new ArrayList<>();
        for (Document documento : movimientos.find(eq("cuentaId", cuentaId)).sort(Sorts.ascending("fecha", "_id"))) {
            resultado.add(new Movimiento(cuentaId, Movimiento.Tipo.valueOf(documento.getString("tipo")),
                    new Dinero(documento.getLong("valorCentavos")), documento.getDate("fecha").toInstant(),
                    documento.getString("referencia")));
        }
        return resultado;
    }
}
```

<!-- file: ejemplo/antes/src/banco/adaptadores/salida/mongo/OrdenesMongo.java -->
```java
package banco.adaptadores.salida.mongo;

import banco.aplicacion.puertos.salida.OrdenesProcesadas;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

/** La clave de la orden como _id: insertar dos veces la misma falla con clave duplicada. */
public final class OrdenesMongo implements OrdenesProcesadas {

    private final MongoCollection<Document> ordenes;

    public OrdenesMongo(MongoDatabase base) {
        this.ordenes = base.getCollection("ordenes_procesadas");
    }

    @Override
    public boolean registrarSiEsNueva(String clave) {
        try {
            ordenes.insertOne(new Document("_id", clave));
            return true;
        } catch (MongoWriteException error) {
            if (error.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                return false;
            }
            throw error;
        }
    }
}
```

<!-- file: ejemplo/antes/src/Main.java -->
```java
import banco.Comprobar;
import banco.ContratoDelRepositorio;
import banco.RepositorioQueFalla;
import banco.adaptadores.salida.mongo.CuentasMongo;
import banco.adaptadores.salida.mongo.MovimientosMongo;
import banco.adaptadores.salida.mongo.OrdenesMongo;
import banco.adaptadores.salida.mongo.UnidadDeTrabajoMongo;
import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.EventoDeCuenta;
import banco.aplicacion.ServicioDeCuentas;
import banco.aplicacion.puertos.entrada.TransferirDinero.Orden;
import banco.aplicacion.puertos.entrada.TransferirDinero.Resultado;
import banco.aplicacion.puertos.salida.Reloj;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * El banco con un primer adaptador de MongoDB. Abrir, depositar, transferir y consultar funcionan;
 * por consola o por HTTP, con un usuario, no se nota nada raro. Las mismas comprobaciones que pasa el adaptador bueno:
 */
public class Main {

    private static final Reloj RELOJ = () -> Instant.parse("2026-11-15T10:00:00Z");

    public static void main(String[] args) {
        try (MongoClient cliente = MongoClients.create("mongodb://localhost:27017/?replicaSet=rs0")) {
            contrato(cliente);
            atomicidad(cliente);
            laMismaOrdenALaVez(cliente);
            muchasTransferenciasALaVez(cliente);
        }
        System.out.println();
        System.out.println(Comprobar.total() + " comprobaciones, " + Comprobar.fallos() + " fallos");
    }

    private record Montaje(ServicioDeCuentas banco, RepositorioQueFalla cuentas, MovimientosMongo movimientos,
                           List<EventoDeCuenta> eventos) {

        static Montaje sobre(MongoClient cliente, String nombreDeLaBase) {
            MongoDatabase base = cliente.getDatabase(nombreDeLaBase);
            base.drop();
            RepositorioQueFalla cuentas = new RepositorioQueFalla(new CuentasMongo(base));
            MovimientosMongo movimientos = new MovimientosMongo(base);
            List<EventoDeCuenta> eventos = Collections.synchronizedList(new ArrayList<>());
            ServicioDeCuentas banco = new ServicioDeCuentas(cuentas, movimientos, new OrdenesMongo(base),
                    new UnidadDeTrabajoMongo(), RELOJ, eventos::add);       // los eventos salen directo, sin bandeja
            return new Montaje(banco, cuentas, movimientos, eventos);
        }
    }

    private static void contrato(MongoClient cliente) {
        System.out.println("1. El contrato del repositorio, el mismo que pasa el almacén en memoria:");
        MongoDatabase base = cliente.getDatabase("nosql04_antes_contrato");
        base.drop();
        Comprobar.igual("CuentasMongo lo cumple entero", List.of(), ContratoDelRepositorio.incumplimientos(() -> new CuentasMongo(base)));
    }

    private static void atomicidad(MongoClient cliente) {
        System.out.println("2. Una transferencia que falla a mitad:");
        Montaje m = Montaje.sobre(cliente, "nosql04_antes_atomicidad");
        String ana = m.banco().abrir("Ana", 100_000_000).id();
        String luis = m.banco().abrir("Luis", 20_000_000).id();

        m.cuentas().fallarAlGuardar(luis);
        Comprobar.lanza("la transferencia falla", IllegalStateException.class,
                () -> m.banco().transferir(new Orden("orden-1", ana, luis, 30_000_000)));
        Comprobar.igual("a Ana no le salió nada", 100_000_000L, saldo(m, ana));
        Comprobar.igual("el extracto de Ana solo tiene la apertura", 1, m.movimientos().de(ana).size());

        m.cuentas().reparar();
        Comprobar.igual("la misma orden se puede reintentar", Resultado.REALIZADA,
                m.banco().transferir(new Orden("orden-1", ana, luis, 30_000_000)));
        Comprobar.igual("Luis recibió los 300.000 pesos", 50_000_000L, saldo(m, luis));
    }

    private static void laMismaOrdenALaVez(MongoClient cliente) {
        System.out.println("3. La misma orden, enviada por 8 hilos a la vez:");
        Montaje m = Montaje.sobre(cliente, "nosql04_antes_idempotencia");
        String ana = m.banco().abrir("Ana", 100_000_000).id();
        String luis = m.banco().abrir("Luis", 0).id();
        AtomicInteger realizadas = new AtomicInteger();
        enParalelo(8, 1, i -> {
            if (m.banco().transferir(new Orden("orden-repetida", ana, luis, 1_000_000)) == Resultado.REALIZADA) {
                realizadas.incrementAndGet();
            }
        });
        Comprobar.igual("se realiza exactamente una vez", 1, realizadas.get());
        Comprobar.igual("y Ana paga exactamente una vez", 99_000_000L, saldo(m, ana));
    }

    private static void muchasTransferenciasALaVez(MongoClient cliente) {
        System.out.println("4. 200 transferencias a la vez entre 4 cuentas, con 8 hilos:");
        Montaje m = Montaje.sobre(cliente, "nosql04_antes_concurrencia");
        List<String> cuentas = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            cuentas.add(m.banco().abrir("Titular " + i, 100_000_000).id());
        }
        AtomicInteger realizadas = new AtomicInteger();
        AtomicInteger rendidas = new AtomicInteger();
        enParalelo(8, 25, i -> {
            String origen = cuentas.get(i % 4);
            String destino = cuentas.get((i + 1 + (i / 4) % 3) % 4);
            try {
                m.banco().transferir(new Orden("orden-" + i, origen, destino, 100_000));
                realizadas.incrementAndGet();
            } catch (ConflictoDeConcurrencia conflicto) {
                rendidas.incrementAndGet();
            }
        });
        long total = cuentas.stream().mapToLong(cuenta -> saldo(m, cuenta)).sum();
        System.out.println("     realizadas " + realizadas.get() + ", abandonadas tras tres conflictos " + rendidas.get());
        Comprobar.igual("el dinero total no cambia", 400_000_000L, total);
    }

    private static long saldo(Montaje m, String cuentaId) {
        return m.banco().porId(cuentaId).orElseThrow().saldoCentavos();
    }

    private interface Tarea {

        void ejecutar(int numero);
    }

    private static void enParalelo(int hilos, int vecesPorHilo, Tarea tarea) {
        List<Future<?>> enviadas = new ArrayList<>();
        try (ExecutorService ejecutor = Executors.newFixedThreadPool(hilos)) {
            for (int h = 0; h < hilos; h++) {
                int hilo = h;
                enviadas.add(ejecutor.submit(() -> {
                    for (int v = 0; v < vecesPorHilo; v++) {
                        tarea.ejecutar(hilo * vecesPorHilo + v);
                    }
                }));
            }
        }
        for (Future<?> enviada : enviadas) {
            try {
                enviada.get();
            } catch (InterruptedException | ExecutionException fallo) {
                throw new IllegalStateException(fallo);
            }
        }
    }
}
```

```text
1. El contrato del repositorio, el mismo que pasa el almacén en memoria:
  FALLA CuentasMongo lo cumple entero: esperaba [] y obtuve [guardar con una versión vieja falla con conflicto]
2. Una transferencia que falla a mitad:
  ok    la transferencia falla
  FALLA a Ana no le salió nada: esperaba 100000000 y obtuve 70000000
  ok    el extracto de Ana solo tiene la apertura
  FALLA la misma orden se puede reintentar: esperaba REALIZADA y obtuve REPETIDA
  FALLA Luis recibió los 300.000 pesos: esperaba 50000000 y obtuve 20000000
3. La misma orden, enviada por 8 hilos a la vez:
  ok    se realiza exactamente una vez
  ok    y Ana paga exactamente una vez
4. 200 transferencias a la vez entre 4 cuentas, con 8 hilos:
     realizadas 200, abandonadas tras tres conflictos 0
  FALLA el dinero total no cambia: esperaba 400000000 y obtuve 402700000

9 comprobaciones, 5 fallos
```

### Análisis

| Comprobación | Lo que pasó |
|---|---|
| 1. Contrato | `replaceOne` con `upsert` guarda lo que le den. El contrato lo detecta en la cláusula de concurrencia, antes de llegar a producción: para eso se escribió una vez y se ejecuta contra cada adaptador |
| 2. Fallo a mitad | A Ana le salieron 300.000 pesos y a Luis no le llegaron. Y la orden quedó registrada como atendida, así que reintentarla responde `REPETIDA`: el dinero no vuelve. Es el `sinTransaccion` de las pruebas del módulo hexagonal, ahora con una base de datos real |
| 3. Misma orden a la vez | Pasa. El `_id` único de MongoDB es atómico, y la idempotencia aguanta. Lo que falla es lo que hay alrededor |
| 4. Concurrencia | El banco tiene 27.000 pesos que no existían (los saldos van en centavos: 402.700.000 en lugar de 400.000.000). Ninguna transferencia detectó un conflicto (0 abandonadas): cada una leyó saldos, los cambió y los escribió encima de lo que otras habían escrito |

Con un usuario, este banco funciona: se abren cuentas, se transfiere, el extracto sale bien. Las cifras del paso 4 cambian en cada ejecución; que el total no cuadra, no.

### Después

<!-- file: ejemplo/despues/src/banco/adaptadores/salida/mongo/UnidadDeTrabajoMongo.java -->
```java
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
```

<!-- file: ejemplo/despues/src/banco/adaptadores/salida/mongo/CuentasMongo.java -->
```java
package banco.adaptadores.salida.mongo;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.puertos.salida.RepositorioDeCuentas;
import banco.dominio.Cuenta;
import banco.dominio.Dinero;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.UpdateResult;
import java.time.LocalDate;
import java.util.Optional;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

/**
 * Una cuenta es un documento:
 *   { _id, titular, saldoCentavos, enviadoHoyCentavos, diaDelTope: "2026-11-15", version }
 * La traducción entre la cuenta y el documento vive aquí y en ningún otro sitio: el dominio no sabe que existe MongoDB.
 */
public final class CuentasMongo implements RepositorioDeCuentas {

    private final MongoCollection<Document> cuentas;
    private final UnidadDeTrabajoMongo transacciones;

    public CuentasMongo(MongoDatabase base, UnidadDeTrabajoMongo transacciones) {
        this.cuentas = base.getCollection(EsquemaMongo.CUENTAS);
        this.transacciones = transacciones;
    }

    /** Un ObjectId se genera en el cliente, sin ir a la base, y no se repite. Un contador en un documento sería un cuello de botella. */
    @Override
    public String siguienteId() {
        return "cuenta-" + new ObjectId().toHexString();
    }

    @Override
    public void guardar(Cuenta cuenta) {
        Document datos = new Document("titular", cuenta.titular())
                .append("saldoCentavos", cuenta.saldo().centavos())
                .append("enviadoHoyCentavos", cuenta.enviadoHoy().centavos())
                .append("diaDelTope", cuenta.diaDelTope().toString())
                .append("version", cuenta.version() + 1);
        if (cuenta.version() == 0) {
            insertar(datos.append("_id", cuenta.id()));
            return;
        }
        // El bloqueo optimista: solo se escribe si la versión guardada sigue siendo la que se leyó.
        Bson mismaVersion = and(eq("_id", cuenta.id()), eq("version", cuenta.version()));
        Bson cambios = new Document("$set", datos);
        UpdateResult resultado = transacciones.sesionEnCurso()
                .map(sesion -> cuentas.updateOne(sesion, mismaVersion, cambios))
                .orElseGet(() -> cuentas.updateOne(mismaVersion, cambios));
        if (resultado.getMatchedCount() == 0) {
            throw new ConflictoDeConcurrencia("la cuenta " + cuenta.id());
        }
    }

    private void insertar(Document documento) {
        try {
            transacciones.sesionEnCurso().ifPresentOrElse(
                    sesion -> cuentas.insertOne(sesion, documento),
                    () -> cuentas.insertOne(documento));
        } catch (MongoWriteException error) {
            if (error.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                throw new ConflictoDeConcurrencia("la cuenta " + documento.getString("_id"));
            }
            throw error;
        }
    }

    @Override
    public Optional<Cuenta> buscar(String id) {
        Document documento = transacciones.sesionEnCurso()
                .map(sesion -> cuentas.find(sesion, eq("_id", id)).first())
                .orElseGet(() -> cuentas.find(eq("_id", id)).first());
        return Optional.ofNullable(documento).map(CuentasMongo::aCuenta);
    }

    /** Cada lectura construye una cuenta nueva: quien la modifica no toca lo guardado. */
    private static Cuenta aCuenta(Document documento) {
        return Cuenta.existente(documento.getString("_id"), documento.getString("titular"),
                new Dinero(documento.getLong("saldoCentavos")), new Dinero(documento.getLong("enviadoHoyCentavos")),
                LocalDate.parse(documento.getString("diaDelTope")), documento.getLong("version"));
    }
}
```

<!-- file: ejemplo/despues/src/banco/adaptadores/salida/mongo/MovimientosMongo.java -->
```java
package banco.adaptadores.salida.mongo;

import static com.mongodb.client.model.Filters.eq;

import banco.aplicacion.puertos.salida.RegistroDeMovimientos;
import banco.dominio.Dinero;
import banco.dominio.Movimiento;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Sorts;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bson.Document;

/** Los movimientos van en su propia colección y referencian la cuenta: crecen sin límite (sesión 01). */
public final class MovimientosMongo implements RegistroDeMovimientos {

    private final MongoCollection<Document> movimientos;
    private final UnidadDeTrabajoMongo transacciones;

    public MovimientosMongo(MongoDatabase base, UnidadDeTrabajoMongo transacciones) {
        this.movimientos = base.getCollection(EsquemaMongo.MOVIMIENTOS);
        this.transacciones = transacciones;
    }

    @Override
    public void registrar(Movimiento movimiento) {
        Document documento = new Document("cuentaId", movimiento.cuentaId())
                .append("tipo", movimiento.tipo().name())
                .append("valorCentavos", movimiento.valor().centavos())
                .append("fecha", Date.from(movimiento.fecha()))
                .append("referencia", movimiento.referencia());
        transacciones.sesionEnCurso().ifPresentOrElse(
                sesion -> movimientos.insertOne(sesion, documento),
                () -> movimientos.insertOne(documento));
    }

    @Override
    public List<Movimiento> de(String cuentaId) {
        FindIterable<Document> consulta = transacciones.sesionEnCurso()
                .map(sesion -> movimientos.find(sesion, eq("cuentaId", cuentaId)))
                .orElseGet(() -> movimientos.find(eq("cuentaId", cuentaId)));
        List<Movimiento> resultado = new ArrayList<>();
        for (Document documento : consulta.sort(Sorts.ascending("fecha", "_id"))) {
            resultado.add(new Movimiento(documento.getString("cuentaId"), Movimiento.Tipo.valueOf(documento.getString("tipo")),
                    new Dinero(documento.getLong("valorCentavos")), documento.getDate("fecha").toInstant(),
                    documento.getString("referencia")));
        }
        return resultado;
    }
}
```

<!-- file: ejemplo/despues/src/banco/adaptadores/salida/mongo/OrdenesMongo.java -->
```java
package banco.adaptadores.salida.mongo;

import static com.mongodb.client.model.Filters.eq;

import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.puertos.salida.OrdenesProcesadas;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.util.Optional;
import org.bson.Document;

/** Las órdenes atendidas. El _id es la clave de la orden: MongoDB garantiza que no se repite. */
public final class OrdenesMongo implements OrdenesProcesadas {

    private final MongoCollection<Document> ordenes;
    private final UnidadDeTrabajoMongo transacciones;

    public OrdenesMongo(MongoDatabase base, UnidadDeTrabajoMongo transacciones) {
        this.ordenes = base.getCollection(EsquemaMongo.ORDENES);
        this.transacciones = transacciones;
    }

    @Override
    public boolean registrarSiEsNueva(String clave) {
        Optional<ClientSession> sesion = transacciones.sesionEnCurso();
        // Dentro de una transacción, un error de clave duplicada la aborta entera. Por eso primero se mira.
        Document existente = sesion.map(s -> ordenes.find(s, eq("_id", clave)).first())
                .orElseGet(() -> ordenes.find(eq("_id", clave)).first());
        if (existente != null) {
            return false;
        }
        Document orden = new Document("_id", clave);
        try {
            sesion.ifPresentOrElse(s -> ordenes.insertOne(s, orden), () -> ordenes.insertOne(orden));
            return true;
        } catch (MongoWriteException error) {
            if (error.getError().getCategory() != ErrorCategory.DUPLICATE_KEY) {
                throw error;
            }
            if (sesion.isPresent()) {
                throw new ConflictoDeConcurrencia("la orden " + clave);   // que se repita entera: la próxima vez la verá
            }
            return false;                                                 // suelta: otro la registró entre mirar e insertar
        }
    }
}
```

<!-- file: ejemplo/despues/src/banco/adaptadores/salida/mongo/BandejaDeSalidaMongo.java -->
```java
package banco.adaptadores.salida.mongo;

import static com.mongodb.client.model.Filters.eq;

import banco.aplicacion.EventoDeCuenta;
import banco.aplicacion.puertos.salida.PublicadorDeEventos;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Sorts;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import org.bson.Document;

/**
 * Publicar un evento es guardarlo en la bandeja de salida, dentro de la misma transacción que el cambio.
 * Otro proceso lo entrega después. Es el outbox del módulo de Kafka, con MongoDB.
 */
public final class BandejaDeSalidaMongo implements PublicadorDeEventos {

    private final MongoCollection<Document> bandeja;
    private final UnidadDeTrabajoMongo transacciones;

    public BandejaDeSalidaMongo(MongoDatabase base, UnidadDeTrabajoMongo transacciones) {
        this.bandeja = base.getCollection(EsquemaMongo.BANDEJA);
        this.transacciones = transacciones;
    }

    @Override
    public void publicar(EventoDeCuenta evento) {
        Document documento = new Document("_id", evento.id())
                .append("tipo", evento.tipo())
                .append("cuentaId", evento.cuentaId())
                .append("centavos", evento.centavos())
                .append("ocurridoEn", Date.from(evento.ocurridoEn()));
        transacciones.sesionEnCurso().ifPresentOrElse(
                sesion -> bandeja.insertOne(sesion, documento),
                () -> bandeja.insertOne(documento));
    }

    /** Entrega lo confirmado, en orden. Si el destino falla, el evento se queda para el siguiente intento. */
    public int entregarPendientes(Consumer<EventoDeCuenta> destino) {
        List<Document> pendientes = bandeja.find().sort(Sorts.ascending("ocurridoEn", "_id")).into(new ArrayList<>());
        int entregados = 0;
        for (Document documento : pendientes) {
            destino.accept(new EventoDeCuenta(documento.getString("_id"), documento.getString("tipo"),
                    documento.getString("cuentaId"), documento.getLong("centavos"), documento.getDate("ocurridoEn").toInstant()));
            bandeja.deleteOne(eq("_id", documento.getString("_id")));     // si se cae aquí, se entregará otra vez: el id permite descartarlo
            entregados++;
        }
        return entregados;
    }
}
```

<!-- file: ejemplo/despues/src/banco/adaptadores/salida/mongo/EsquemaMongo.java -->
```java
package banco.adaptadores.salida.mongo;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import java.util.ArrayList;
import java.util.List;

/**
 * MongoDB no exige esquema, pero el banco sí tiene uno: estas colecciones y estos índices.
 * Se crean al arrancar, antes de la primera transacción, y crearlos dos veces no hace nada.
 */
public final class EsquemaMongo {

    static final String CUENTAS = "cuentas";
    static final String MOVIMIENTOS = "movimientos";
    static final String ORDENES = "ordenes_procesadas";
    static final String BANDEJA = "bandeja_de_salida";

    private EsquemaMongo() {
    }

    public static void preparar(MongoDatabase base) {
        List<String> existentes = base.listCollectionNames().into(new ArrayList<>());
        for (String coleccion : List.of(CUENTAS, MOVIMIENTOS, ORDENES, BANDEJA)) {
            if (!existentes.contains(coleccion)) {
                base.createCollection(coleccion);
            }
        }
        // El extracto de una cuenta, en orden (sesión 02). Las cuentas, las órdenes y los eventos se buscan por _id.
        base.getCollection(MOVIMIENTOS).createIndex(
                Indexes.compoundIndex(Indexes.ascending("cuentaId"), Indexes.ascending("fecha")),
                new IndexOptions().name("extracto"));
        base.getCollection(BANDEJA).createIndex(Indexes.ascending("ocurridoEn"), new IndexOptions().name("por_entregar"));
    }
}
```

<!-- file: ejemplo/despues/src/Main.java -->
```java
import banco.Comprobar;
import banco.ContratoDelRepositorio;
import banco.RepositorioQueFalla;
import banco.adaptadores.salida.mongo.BandejaDeSalidaMongo;
import banco.adaptadores.salida.mongo.CuentasMongo;
import banco.adaptadores.salida.mongo.EsquemaMongo;
import banco.adaptadores.salida.mongo.MovimientosMongo;
import banco.adaptadores.salida.mongo.OrdenesMongo;
import banco.adaptadores.salida.mongo.UnidadDeTrabajoMongo;
import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.EventoDeCuenta;
import banco.aplicacion.ServicioDeCuentas;
import banco.aplicacion.puertos.entrada.TransferirDinero.Orden;
import banco.aplicacion.puertos.entrada.TransferirDinero.Resultado;
import banco.aplicacion.puertos.salida.Reloj;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/** El banco del módulo hexagonal, sin cambiar una línea de su núcleo, guardando en MongoDB. */
public class Main {

    private static final Reloj RELOJ = () -> Instant.parse("2026-11-15T10:00:00Z");

    public static void main(String[] args) {
        try (MongoClient cliente = MongoClients.create("mongodb://localhost:27017/?replicaSet=rs0")) {
            contrato(cliente);
            atomicidad(cliente);
            laMismaOrdenALaVez(cliente);
            muchasTransferenciasALaVez(cliente);
            bandejaDeSalida(cliente);
        }
        System.out.println();
        System.out.println(Comprobar.total() + " comprobaciones, " + Comprobar.fallos() + " fallos");
        if (Comprobar.fallos() > 0) {
            System.exit(1);
        }
    }

    /** La raíz de composición: los mismos puertos de siempre, con adaptadores de MongoDB. */
    private record Montaje(ServicioDeCuentas banco, RepositorioQueFalla cuentas, MovimientosMongo movimientos,
                           BandejaDeSalidaMongo bandeja) {

        static Montaje sobre(MongoClient cliente, String nombreDeLaBase) {
            MongoDatabase base = cliente.getDatabase(nombreDeLaBase);
            base.drop();
            EsquemaMongo.preparar(base);
            UnidadDeTrabajoMongo transacciones = new UnidadDeTrabajoMongo(cliente);
            RepositorioQueFalla cuentas = new RepositorioQueFalla(new CuentasMongo(base, transacciones));
            MovimientosMongo movimientos = new MovimientosMongo(base, transacciones);
            BandejaDeSalidaMongo bandeja = new BandejaDeSalidaMongo(base, transacciones);
            ServicioDeCuentas banco = new ServicioDeCuentas(cuentas, movimientos, new OrdenesMongo(base, transacciones),
                    transacciones, RELOJ, bandeja);
            return new Montaje(banco, cuentas, movimientos, bandeja);
        }
    }

    private static void contrato(MongoClient cliente) {
        System.out.println("1. El contrato del repositorio, el mismo que pasa el almacén en memoria:");
        MongoDatabase base = cliente.getDatabase("nosql04_despues_contrato");
        base.drop();
        EsquemaMongo.preparar(base);
        UnidadDeTrabajoMongo transacciones = new UnidadDeTrabajoMongo(cliente);
        Comprobar.igual("CuentasMongo lo cumple entero", List.of(),
                ContratoDelRepositorio.incumplimientos(() -> new CuentasMongo(base, transacciones)));
    }

    private static void atomicidad(MongoClient cliente) {
        System.out.println("2. Una transferencia que falla a mitad:");
        Montaje m = Montaje.sobre(cliente, "nosql04_despues_atomicidad");
        String ana = m.banco().abrir("Ana", 100_000_000).id();
        String luis = m.banco().abrir("Luis", 20_000_000).id();

        m.cuentas().fallarAlGuardar(luis);                       // el fallo ocurre entre el débito y el abono
        Comprobar.lanza("la transferencia falla", IllegalStateException.class,
                () -> m.banco().transferir(new Orden("orden-1", ana, luis, 30_000_000)));
        Comprobar.igual("a Ana no le salió nada", 100_000_000L, saldo(m, ana));
        Comprobar.igual("el extracto de Ana solo tiene la apertura", 1, m.movimientos().de(ana).size());

        m.cuentas().reparar();
        Comprobar.igual("la misma orden se puede reintentar", Resultado.REALIZADA,
                m.banco().transferir(new Orden("orden-1", ana, luis, 30_000_000)));
        Comprobar.igual("y enviarla otra vez no transfiere de nuevo", Resultado.REPETIDA,
                m.banco().transferir(new Orden("orden-1", ana, luis, 30_000_000)));
        Comprobar.igual("Ana pagó una sola vez", 70_000_000L, saldo(m, ana));
        Comprobar.igual("Luis recibió una sola vez", 50_000_000L, saldo(m, luis));
    }

    private static void laMismaOrdenALaVez(MongoClient cliente) {
        System.out.println("3. La misma orden, enviada por 8 hilos a la vez:");
        Montaje m = Montaje.sobre(cliente, "nosql04_despues_idempotencia");
        String ana = m.banco().abrir("Ana", 100_000_000).id();
        String luis = m.banco().abrir("Luis", 0).id();
        AtomicInteger realizadas = new AtomicInteger();
        enParalelo(8, 1, i -> {
            try {
                if (m.banco().transferir(new Orden("orden-repetida", ana, luis, 1_000_000)) == Resultado.REALIZADA) {
                    realizadas.incrementAndGet();
                }
            } catch (ConflictoDeConcurrencia rendida) {
                // tras tres intentos, el caso de uso se rinde: no transfirió nada
            }
        });
        Comprobar.igual("se realiza exactamente una vez", 1, realizadas.get());
        Comprobar.igual("y Ana paga exactamente una vez", 99_000_000L, saldo(m, ana));
    }

    private static void muchasTransferenciasALaVez(MongoClient cliente) {
        System.out.println("4. 200 transferencias a la vez entre 4 cuentas, con 8 hilos:");
        Montaje m = Montaje.sobre(cliente, "nosql04_despues_concurrencia");
        List<String> cuentas = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            cuentas.add(m.banco().abrir("Titular " + i, 100_000_000).id());
        }
        AtomicInteger realizadas = new AtomicInteger();
        AtomicInteger rendidas = new AtomicInteger();
        enParalelo(8, 25, i -> {
            String origen = cuentas.get(i % 4);
            String destino = cuentas.get((i + 1 + (i / 4) % 3) % 4);
            try {
                m.banco().transferir(new Orden("orden-" + i, origen, destino, 100_000));
                realizadas.incrementAndGet();
            } catch (ConflictoDeConcurrencia conflicto) {
                rendidas.incrementAndGet();
            }
        });
        long total = cuentas.stream().mapToLong(cuenta -> saldo(m, cuenta)).sum();
        int movimientos = cuentas.stream().mapToInt(cuenta -> m.movimientos().de(cuenta).size()).sum();
        System.out.println("     realizadas " + realizadas.get() + ", abandonadas tras tres conflictos " + rendidas.get());
        Comprobar.igual("ninguna se pierde: o se realizó o se abandonó entera", 200, realizadas.get() + rendidas.get());
        Comprobar.igual("el dinero total no cambia", 400_000_000L, total);
        Comprobar.igual("cada transferencia realizada dejó dos movimientos", 4 + 2 * realizadas.get(), movimientos);
        Comprobar.igual("y un evento en la bandeja de salida", realizadas.get(), m.bandeja().entregarPendientes(evento -> { }));
    }

    private static void bandejaDeSalida(MongoClient cliente) {
        System.out.println("5. La bandeja de salida:");
        Montaje m = Montaje.sobre(cliente, "nosql04_despues_bandeja");
        String ana = m.banco().abrir("Ana", 100_000_000).id();
        String luis = m.banco().abrir("Luis", 20_000_000).id();
        List<EventoDeCuenta> recibidos = new ArrayList<>();

        m.cuentas().fallarAlGuardar(luis);
        Comprobar.lanza("una transferencia que falla", IllegalStateException.class,
                () -> m.banco().transferir(new Orden("orden-1", ana, luis, 30_000_000)));
        Comprobar.igual("no deja ningún evento por entregar", 0, m.bandeja().entregarPendientes(recibidos::add));

        m.cuentas().reparar();
        m.banco().transferir(new Orden("orden-1", ana, luis, 30_000_000));
        Comprobar.lanza("si el destino de los eventos está caído, la entrega falla", IllegalStateException.class,
                () -> m.bandeja().entregarPendientes(evento -> {
                    throw new IllegalStateException("broker no disponible");
                }));
        Comprobar.igual("pero el evento no se pierde: sale en el siguiente intento", 1, m.bandeja().entregarPendientes(recibidos::add));
        Comprobar.igual("con un id estable para que el receptor descarte repeticiones", "transferencia:orden-1", recibidos.get(0).id());
    }

    private static long saldo(Montaje m, String cuentaId) {
        return m.banco().porId(cuentaId).orElseThrow().saldoCentavos();
    }

    /** Una tarea numerada, para que cada hilo sepa qué orden le toca. */
    private interface Tarea {

        void ejecutar(int numero);
    }

    private static void enParalelo(int hilos, int vecesPorHilo, Tarea tarea) {
        List<Future<?>> enviadas = new ArrayList<>();
        try (ExecutorService ejecutor = Executors.newFixedThreadPool(hilos)) {
            for (int h = 0; h < hilos; h++) {
                int hilo = h;
                enviadas.add(ejecutor.submit(() -> {
                    for (int v = 0; v < vecesPorHilo; v++) {
                        tarea.ejecutar(hilo * vecesPorHilo + v);
                    }
                }));
            }
        }
        for (Future<?> enviada : enviadas) {
            try {
                enviada.get();
            } catch (InterruptedException | ExecutionException fallo) {
                throw new IllegalStateException(fallo);
            }
        }
    }
}
```

```text
1. El contrato del repositorio, el mismo que pasa el almacén en memoria:
  ok    CuentasMongo lo cumple entero
2. Una transferencia que falla a mitad:
  ok    la transferencia falla
  ok    a Ana no le salió nada
  ok    el extracto de Ana solo tiene la apertura
  ok    la misma orden se puede reintentar
  ok    y enviarla otra vez no transfiere de nuevo
  ok    Ana pagó una sola vez
  ok    Luis recibió una sola vez
3. La misma orden, enviada por 8 hilos a la vez:
  ok    se realiza exactamente una vez
  ok    y Ana paga exactamente una vez
4. 200 transferencias a la vez entre 4 cuentas, con 8 hilos:
     realizadas 73, abandonadas tras tres conflictos 127
  ok    ninguna se pierde: o se realizó o se abandonó entera
  ok    el dinero total no cambia
  ok    cada transferencia realizada dejó dos movimientos
  ok    y un evento en la bandeja de salida
5. La bandeja de salida:
  ok    una transferencia que falla
  ok    no deja ningún evento por entregar
  ok    si el destino de los eventos está caído, la entrega falla
  ok    pero el evento no se pierde: sale en el siguiente intento
  ok    con un id estable para que el receptor descarte repeticiones

19 comprobaciones, 0 fallos
```

- **El contrato se cumple entero**, el mismo que cumple `AlmacenEnMemoria`. Por eso las pruebas rápidas del núcleo, que usan el almacén en memoria, también hablan de lo que pasará con MongoDB.
- **El fallo a mitad no deja nada**: ni débito, ni movimientos, ni la orden marcada, ni un evento en la bandeja. La orden se puede reintentar y se realiza una sola vez.
- **La concurrencia no pierde dinero.** Cada transferencia se realizó entera o se abandonó entera, y por cada realizada hay dos movimientos y un evento.
- **El dominio y la aplicación no cambiaron.** Los dos ejemplos compilan el mismo `hexagonal/proyecto/src`.

Hay un dato que merece una segunda lectura: **127 de 200 transferencias se abandonaron** tras tres conflictos. Es correcto (nada se perdió) pero es mal servicio. Ocho hilos sobre cuatro cuentas es una contienda altísima, y MongoDB no hace esperar a la segunda transacción como haría un bloqueo de fila: la rechaza al instante. `ServicioDeCuentas` reintenta tres veces seguidas, sin pausa, así que los reintentos chocan otra vez con las mismas transacciones. Las salidas posibles son decisiones de diseño, no de MongoDB: reintentos con espera creciente y azar (eso es un cambio en la aplicación, no en el adaptador), menos contienda en el modelo, o aceptar el rechazo y devolverlo al cliente. El número exacto cambia en cada ejecución.

## Errores comunes

- Anotar las clases del dominio para que el mapeador las guarde tal cual: el dominio queda atado a MongoDB.
- Repositorios que abren su propia sesión, o ninguna, en lugar de usar la de la unidad de trabajo.
- Una unidad de trabajo anidada que abre otra transacción en lugar de unirse a la abierta.
- Dejar que `MongoException` llegue a la aplicación, o al cliente HTTP.
- Capturar una clave duplicada dentro de una transacción y seguir como si nada.
- Crear colecciones o índices dentro de la primera transacción.
- Probar el adaptador solo con un MongoDB simulado: lo que hay que comprobar es justamente cómo se comporta el de verdad.
- Ejecutar el contrato contra el adaptador de memoria y no contra el de MongoDB.

## Taller

**Objetivo:** conectar tu servicio de pedidos a MongoDB con adaptadores que cumplan los mismos contratos que tus adaptadores en memoria, sin tocar su dominio ni su aplicación.

Sobre tu servicio de pedidos del [módulo hexagonal](../../hexagonal/) y el [entorno](../entorno/) de esta ruta, con el controlador de Java y sin Spring.

1. Escribe el adaptador de tu puerto de transacciones con `ClientSession`. Debe ser el único sitio del proyecto que abre sesiones y transacciones, y una unidad de trabajo anidada debe unirse a la abierta.
2. Escribe con MongoDB los repositorios que necesite "crear pedido", con el modelo de tu [taller 01](../01-modelar-por-consultas/) y los índices de tu [taller 02](../02-indices-y-explain/).
3. Ejecuta contra ellos, con MongoDB real, el contrato de repositorio que escribiste en el módulo hexagonal. Si tu contrato no tiene una cláusula de concurrencia, añádela primero y comprueba que tu adaptador en memoria la sigue cumpliendo.
4. Traduce los errores: versión que no coincide, `WriteConflict` y clave duplicada. Comprueba con una prueba que ninguna clase de `com.mongodb` aparece fuera del paquete del adaptador.
5. Provoca un fallo a mitad de "crear pedido" (con un repositorio que falla a propósito, como `RepositorioQueFalla`) y demuestra que no queda nada.
6. Lanza 200 pedidos concurrentes sobre los mismos tres productos. Mide cuántos se realizan, cuántos se rechazan por existencias y cuántos se abandonan por conflicto.
7. Si los abandonos por conflicto te parecen inaceptables, cambia la política de reintento de tu aplicación (espera creciente con azar, más intentos, o devolver el rechazo al cliente) y repite la medición. Justifica la decisión.
8. Cambia la raíz de composición para elegir entre memoria y MongoDB con un parámetro. El resto del código no debe enterarse.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| El dominio y la aplicación no cambiaron | `git diff` de esos paquetes; si cambiaste la política de reintento en el paso 7, ese es el único cambio y está justificado |
| El contrato pasa contra memoria y contra MongoDB | Salida del paso 3 |
| `com.mongodb` solo aparece en el adaptador | Prueba del paso 4 |
| Un fallo a mitad no deja datos | Paso 5 |
| Bajo concurrencia, el total de existencias cuadra | Paso 6 |

**Entrega:** rama `taller/nosql-04`, con el código, las salidas y `ANALISIS.md` en `taller/solucion/`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

Con Spring Data MongoDB el adaptador se escribe con `MongoTemplate` o con repositorios de Spring Data, `@Version` en la clase persistida, y `@Transactional` con un `MongoTransactionManager` declarado. Lo que no cambia es la forma: las clases que se guardan son del adaptador, no del dominio, y se traducen en un solo sitio. Spring traduce los errores de MongoDB a su jerarquía (`OptimisticLockingFailureException`, `DuplicateKeyException`…); el adaptador sigue siendo quien los convierte a las excepciones de tu aplicación.

El contrato se ejecuta en la integración continua contra un MongoDB de verdad, levantado en un contenedor (Testcontainers es la forma habitual en Java). Un contrato que solo se ejecuta contra el adaptador en memoria prueba el adaptador en memoria.

## Preguntas senior

1. ¿Qué pieza de una arquitectura hexagonal abre la sesión y la transacción de MongoDB, y por qué solo esa?
2. ¿Por qué el adaptador traduce `WriteConflict` a `ConflictoDeConcurrencia` en lugar de reintentar él mismo?
3. ¿Qué cláusula del contrato habría dejado pasar un error grave si solo se ejecutara contra memoria?
4. ¿Por qué no se captura una clave duplicada dentro de una transacción, y qué haces en su lugar?
5. Con mucha contienda sobre las mismas cuentas, la mayoría de las transferencias se abandonan. ¿Qué opciones tienes y en qué capa vive cada una?
6. ¿Cómo eliges entre memoria y MongoDB sin que el resto del código se entere?
