# 01 — Modelar por consultas

> Requiere la lección [NoSQL y caché](../../java/nivel-08-bases-de-datos/06-nosql-y-cache/) del Nivel 8 y el [entorno](../entorno/) levantado.

## Idea

La billetera. Llevas en ella lo que usas siempre y cabe: la cédula, un par de tarjetas, algo de efectivo. Los recibos de cada compra no: si los guardaras todos ahí, al año no cerraría, y para sacar la cédula tendrías que sacar mil papeles. Los recibos van a una caja, ordenados por fecha, y cuando necesitas uno vas a buscarlo.

Un documento de MongoDB es la billetera: lo que se lee junto y no crece. La caja de recibos es otra colección.

## Lo esencial

### De las entidades a las consultas

En el modelo relacional se normalizan las entidades y después cualquier consulta se arma con uniones. En MongoDB **el documento es la unidad de lectura y la unidad de escritura atómica**: lo que está en un documento se lee de una vez y se cambia de una vez; lo que está en dos documentos, no. Por eso el diseño empieza al revés: primero se listan las consultas y las escrituras, con su frecuencia, y después se decide qué va en cada documento.

Los patrones de acceso del banco:

| Consulta o escritura | Frecuencia | Qué necesita |
|---|---|---|
| Ver saldo y titular de una cuenta | Muy alta | Una cuenta |
| Ver los últimos 10 movimientos | Alta | Los movimientos de una cuenta, recientes primero |
| Registrar un movimiento | Alta | Agregar un movimiento y cambiar el saldo |
| Extracto de un mes | Media | Los movimientos de una cuenta en un rango de fechas |
| Conciliación del día | Baja, por lotes | Todos los movimientos de un día |

Con esa tabla delante, la pregunta "¿los movimientos van dentro de la cuenta?" tiene respuesta: la consulta más frecuente necesita la cuenta **sin** sus movimientos, y los movimientos crecen sin límite.

### Incrustar o referenciar

| Incrusta cuando | Referencia cuando |
|---|---|
| Se lee siempre junto con el documento que lo contiene | Se lee por separado, o paginado |
| Está acotado: pocas decenas, tamaño conocido | Crece sin límite: movimientos, comentarios, eventos |
| No tiene vida propia fuera del padre | Lo comparten varios documentos o se consulta solo |
| Cambia junto con el padre, y así la atomicidad sale gratis | Cambia a otro ritmo, o muy a menudo |

El límite duro es de **16 MB por documento**. El límite práctico llega mucho antes: cada lectura del documento lo trae entero (o, con una proyección, lo carga entero en el servidor aunque devuelva un trozo), cada actualización reescribe un documento grande, y un arreglo enorme hace enormes también sus índices.

### Patrones con nombre

| Patrón | Qué es | En el banco |
|---|---|---|
| Incrustado | Un subdocumento o un arreglo acotado | El titular dentro de la cuenta |
| Referencia | Se guarda el `_id` del otro documento | `cuentaId` en cada movimiento |
| Referencia extendida | La referencia más una copia de los campos que se leen con ella | El nombre del titular copiado en el movimiento, para el extracto |
| Subconjunto | Los N más recientes incrustados, el resto aparte | Los últimos 5 movimientos dentro de la cuenta, para la pantalla principal |
| Cubeta | Elementos agrupados por rango en documentos con tope | Un documento por cuenta y día con sus movimientos; para series de tiempo, MongoDB tiene colecciones propias |
| Valor calculado | El resultado de una agregación, guardado | El saldo: es la suma de los movimientos |

Duplicar datos es legítimo en documentos, pero cada copia exige una decisión: o se actualiza cuando cambia el original (¿quién, cuándo, con qué garantía?), o es una foto histórica a propósito. El nombre del titular en un movimiento debe ser el que tenía ese día: ahí la copia es la verdad.

### El esquema existe igual

MongoDB no exige un esquema, pero tu código sí tiene uno: los nombres de los campos, sus tipos y qué es obligatorio. Si no está en la base, está repartido por las clases que leen y escriben. Se puede declarar con un validador `$jsonSchema` en la colección; como mínimo, debe estar escrito y versionado en el adaptador.

## Ejemplo

Una cuenta que recibe movimientos hasta 200.000, medida a cada paso: tamaño del documento, tiempo de leer el saldo y tiempo de leer los últimos 10. Cada carpeta es un proyecto Maven que hereda del [`pom.xml`](../pom.xml) del módulo:

<!-- file: ejemplo/antes/pom.xml -->
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

    <artifactId>nosql-01-antes</artifactId>

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
</project>
```

Desde `ejemplo/antes` y luego desde `ejemplo/despues`:

```bash
mvn -q compile exec:java
```

### Antes

<!-- file: ejemplo/antes/src/Main.java -->
```java
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
```

```text
movimientos | tamaño del documento | leer el saldo | últimos 10
      1.000 |           100.853 B |        2,7 ms |     2,7 ms
     10.000 |         1.027.853 B |        9,8 ms |     9,2 ms
     50.000 |         5.227.853 B |       33,2 ms |    35,1 ms
    100.000 |        10.477.853 B |       65,5 ms |    64,1 ms
    150.000 |        15.827.853 B |       99,1 ms |    98,7 ms
    150.000 | la cuenta ya no acepta movimientos: código 10334
            Write operation error on MongoDB server localhost:27017. Write error: WriteError{code=10334, message='Plan executor error during update :: caused by :: Resulting document after update is larger than 16777216', details={}}.
Saldo según la cuenta: 150.000.000 centavos
```

### Análisis

| Lo que se ve | Lo que pasa |
|---|---|
| El documento crece unos 105 bytes por movimiento | Cada movimiento queda dentro de la cuenta, para siempre |
| Leer el saldo pasa de 2 ms a casi 100 ms | Para leer un número se trae el documento entero: con 150.000 movimientos, 15 MB por la red en cada consulta |
| "Últimos 10" cuesta lo mismo que leer el saldo | Se trae el arreglo entero y se corta en Java. Con una proyección `$slice` viajarían solo 10, pero el servidor seguiría cargando el documento entero en memoria |
| A los 150.000 movimientos la cuenta deja de aceptar más | El documento llegó a 16 MB: error 10334. No falla una consulta; falla el negocio: la cuenta no puede recibir dinero |
| Nadie lo nota en desarrollo | Con 50 movimientos de prueba todo va bien. Una cuenta empresarial con 400 movimientos diarios llega al límite en un año |

El diseño se decidió mirando la entidad ("los movimientos son de la cuenta") y no las consultas. Las milésimas exactas dependen de tu máquina; la forma de la curva, no.

### Después

Dos colecciones. La cuenta incrusta lo que se lee con ella y está acotado (el titular). Los movimientos van aparte, referencian la cuenta, y tienen un índice hecho para la consulta "los de esta cuenta, recientes primero" (la [sesión 02](../02-indices-y-explain/) explica cómo se elige).

<!-- file: ejemplo/despues/src/Main.java -->
```java
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
```

```text
movimientos | tamaño de la cuenta | leer el saldo | últimos 10
      1.000 |               97 B |        0,7 ms |     0,9 ms
     10.000 |               97 B |        0,9 ms |     0,8 ms
     50.000 |               97 B |        0,6 ms |     0,7 ms
    100.000 |               97 B |        0,3 ms |     0,4 ms
    150.000 |               97 B |        0,6 ms |     0,7 ms
    200.000 |               97 B |        0,4 ms |     0,4 ms
Saldo según la cuenta: 200.000.000 centavos
Movimientos guardados: 200.000
El más reciente: consignación número 199999
```

- La cuenta mide lo mismo con 1.000 movimientos que con 200.000. Leer el saldo cuesta lo mismo siempre.
- "Últimos 10" lo resuelve la base: ordena con el índice, corta y envía diez documentos.
- Ya no hay límite de movimientos por cuenta: cada uno es su propio documento.

Lo que se pagó: registrar un movimiento son **dos escrituras** en dos documentos. Si el proceso cae entre la primera y la segunda, el saldo y los movimientos no cuadran. Eso no se arregla con el modelo sino con una transacción, y es la [sesión 03](../03-consistencia-en-mongodb/).

## Errores comunes

- Traducir las tablas a colecciones una a una y "unirlas" desde Java con varias consultas.
- Incrustar todo "porque en MongoDB se incrusta", sin preguntar si crece.
- Referenciar todo "por normalizar", y hacer diez consultas para pintar una pantalla.
- Arreglos sin tope: comentarios, historial, eventos, auditoría.
- Leer el documento entero para usar un campo. Una proyección ayuda a la red, no al servidor.
- Duplicar un dato sin decidir si es una foto histórica o una copia que hay que mantener.
- Creer que no hay esquema y descubrirlo cuando tres versiones del documento conviven en producción.

## Taller

**Objetivo:** modelar en documentos tu servicio de pedidos, partiendo de sus consultas, y demostrar con volumen que el modelo aguanta.

Sobre tu servicio de pedidos del [módulo hexagonal](../../hexagonal/). Con el controlador de Java, sin Spring.

1. Lista al menos seis consultas y escrituras del servicio, con su frecuencia y el volumen que esperas por entidad en tres años (pedidos por cliente, líneas por pedido, cambios de estado por pedido).
2. Diseña los documentos. Para cada relación (pedido y líneas, pedido y cliente, línea y producto, pedido y sus cambios de estado) escribe si incrustas, referencias o usas una referencia extendida, y por qué, con los criterios de esta sesión.
3. Decide qué hacer con el precio del producto en una línea de pedido. ¿Copia o referencia? ¿Qué pasa si el precio del catálogo cambia mañana?
4. Escribe un programa que cargue un volumen realista (por ejemplo, 50.000 clientes y 1.000.000 de pedidos, con un cliente muy activo) y mida con `$bsonSize` el tamaño medio y máximo de cada tipo de documento.
5. Mide el tiempo de tus tres consultas más frecuentes con ese volumen.
6. Implementa también el antipatrón: los pedidos dentro del documento del cliente. Calcula con tus mediciones cuándo llega al límite tu cliente más activo, y compruébalo.
7. Añade un validador `$jsonSchema` a la colección de pedidos y demuestra que rechaza un pedido sin cliente o con un total negativo.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| Cada decisión de incrustar o referenciar cita una consulta y un volumen | `ANALISIS.md`, pasos 1 y 2 |
| Ningún documento tiene un arreglo sin tope | Tamaños máximos del paso 4 |
| El antipatrón falla donde dijiste que fallaría | Paso 6, con la salida |
| La colección rechaza documentos mal formados | Paso 7, con la salida |
| El precio de la línea tiene una política explícita | Paso 3 |

**Entrega:** rama `taller/nosql-01`, con el programa, las mediciones y `ANALISIS.md` en `taller/solucion/`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

Con Spring Data MongoDB, las clases anotadas con `@Document` se guardan tal cual, con sus listas incrustadas. La trampa habitual es anotar el grafo de entidades de JPA y reemplazar cada relación por una referencia: el resultado es el modelo relacional disfrazado, con muchas consultas por pantalla y ninguna unión que las junte. El modelo de documentos se decide antes de escribir la primera clase, con la tabla de consultas en la mano, y conviene dejarlo escrito en un registro de decisiones.

Los documentos cambian de forma con el tiempo y las versiones viejas no desaparecen solas. Un campo `version` del esquema en cada documento y una migración perezosa (se actualiza al leerlo) o por lotes son la práctica habitual.

## Preguntas senior

1. ¿Por qué en una base de documentos se modela a partir de las consultas y no de las entidades?
2. Dame tres criterios para incrustar y tres para referenciar. ¿Cuál pesa más y por qué?
3. ¿Qué problemas aparecen con un arreglo que crece sin límite mucho antes de llegar a los 16 MB?
4. ¿Cuándo duplicas un dato en otro documento y cómo decides si hay que mantener la copia?
5. Alguien dice que MongoDB "no tiene esquema". ¿Dónde está el esquema y qué riesgo tiene que no esté en la base?
6. ¿Con qué consultas de tu servicio de pedidos este modelo sería peor que uno relacional?
