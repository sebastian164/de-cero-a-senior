# 02 — Índices y explain

> Requiere la [sesión 01](../01-modelar-por-consultas/) y la lección de [índices y planes](../../java/nivel-08-bases-de-datos/02-indices-y-planes/) del Nivel 8: las ideas de árbol B, selectividad y plan de ejecución son las mismas.

## Idea

La guía telefónica de papel está ordenada por apellido y, dentro de cada apellido, por nombre. Encontrar "todos los López, por orden de nombre" es abrir por la L y leer seguido. Encontrar "todos los que se llaman Juan" obliga a leer la guía entera, aunque esté ordenada: está ordenada por otra cosa.

Un índice compuesto es esa guía. Lo que importa no es qué campos tiene, sino **en qué orden** los tiene.

## Lo esencial

### Leer un plan

`explain("executionStats")` devuelve el plan que eligió MongoDB y lo que costó ejecutarlo. Lo primero que se mira:

| Campo | Qué dice |
|---|---|
| `winningPlan` | Las etapas, de dentro hacia fuera: `IXSCAN` recorre un índice, `COLLSCAN` recorre la colección entera, `FETCH` trae el documento, `SORT` ordena en memoria, `SKIP` y `LIMIT` descartan y cortan |
| `totalKeysExamined` | Entradas del índice que se recorrieron |
| `totalDocsExamined` | Documentos que se cargaron |
| `nReturned` | Documentos que se devolvieron |
| `executionTimeMillis` | Tiempo en el servidor. Con pocos datos engaña: mira los otros tres |

La cifra que importa es la **proporción entre lo examinado y lo devuelto**. Lo ideal es 1: se leyó exactamente lo que se devolvió. `IXSCAN` no significa "bien": un índice recorrido casi entero para devolver veinte documentos es tan malo como no tenerlo.

### El orden de los campos: la regla ESR

Para una consulta con igualdades, un orden y un rango, el índice compuesto se arma así:

1. **E**quality: primero los campos que se comparan por igualdad (`cuentaId = …`, `tipo = …`).
2. **S**ort: después el campo por el que se ordena (`fecha` descendente).
3. **R**ange: al final los campos con rango (`valor > …`, `fecha entre …`).

Con las igualdades delante, todas las entradas que interesan quedan juntas en el índice; con el orden detrás, ya salen ordenadas y no hace falta una etapa `SORT`. Si el campo de orden y el de rango son el mismo (`fecha`), un índice sirve para las dos cosas.

Un índice también sirve para cualquier **prefijo** suyo: `{cuentaId, fecha}` resuelve "los movimientos de esta cuenta", pero no "los movimientos de este día" (le falta el primer campo).

### Paginar

`skip(n)` no salta: recorre y descarta. La página 1.000 de 20 examina 20.020 entradas. La alternativa es **paginar por clave** (keyset): la página siguiente pide "lo anterior a la última fecha que vi", y el índice va directo a ese punto. Si la clave de orden puede repetirse, se añade un desempate (`fecha` y `_id`, en el índice, en el orden y en la condición).

### Lo que cuesta un índice

- Cada escritura actualiza **todos** los índices de la colección.
- Los índices ocupan memoria, y lo que no cabe en memoria va a disco.
- Un índice que nadie usa es solo coste. `$indexStats` dice cuántas veces se ha usado cada uno.
- Una consulta **cubierta** (que solo pide campos del índice y excluye `_id`) no carga documentos: `totalDocsExamined` es 0.

## Ejemplo

200.000 movimientos de 1.000 cuentas, unos 200 por cuenta, repartidos al azar en el tiempo (con semilla fija, para que todos veamos los mismos datos). Cada ejemplo carga los datos, crea sus índices y muestra el resumen de `explain` de cada consulta.

### Antes

Alguien creó un índice por fecha "porque todo se ordena por fecha", y el extracto pagina con `skip`.

<!-- file: ejemplo/antes/src/Main.java -->
```java
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
```

```text
consulta                         | plan                     |  claves | documentos | devueltos |  ms
extracto: 20 más recientes       | LIMIT<FETCH<IXSCAN       |  17.393 |     17.393 |        20 |   7
extracto de un rango de fechas   | FETCH<IXSCAN             |  50.000 |     50.000 |        57 |  15
página 9 (skip 160)              | LIMIT<SKIP<FETCH<IXSCAN  | 170.825 |    170.825 |        20 |  62
Índices: [_id_, fecha_-1]
```

### Análisis

| Consulta | Lo que pasó |
|---|---|
| Extracto, 20 más recientes | El índice por fecha da el orden, pero no encuentra la cuenta. MongoDB recorre las fechas hacia atrás, de todas las cuentas, hasta juntar 20 de la cuenta 500: 17.393 documentos cargados para devolver 20 |
| Rango de fechas | El índice acota el rango (50.000 movimientos de todas las cuentas) y después filtra la cuenta documento por documento: 50.000 para 57 |
| Página 9 con `skip` | Para descartar los 160 primeros de la cuenta hay que encontrarlos, y encontrarlos cuesta lo mismo que antes: 170.825 documentos para 20 |
| Los tiempos | 7, 15 y 62 ms en un portátil sin carga. Con 200 millones de movimientos en lugar de 200.000 y cien consultas por segundo, es la base de datos entera dedicada a esto |

Las tres consultas dicen `IXSCAN`. Un panel que solo vigile "¿usa índice?" las daría por buenas.

### Después

Un índice por consulta, armado con ESR, y paginación por clave. Para comparar, el ejemplo crea un momento el índice con los mismos campos en el orden equivocado y lo fuerza con `hint`.

<!-- file: ejemplo/despues/src/Main.java -->
```java
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
 * Los mismos datos y las mismas consultas. Los índices salen de las consultas, con la regla ESR:
 * primero los campos de igualdad, luego el de orden, luego los de rango. Y el extracto pagina por clave, no con skip.
 */
public class Main {

    private static final int CUENTAS = 1_000;
    private static final int MOVIMIENTOS = 200_000;
    private static final long INICIO = 1_763_200_000_000L;
    private static final Locale ES = Locale.forLanguageTag("es-CO");

    public static void main(String[] args) {
        try (MongoClient cliente = MongoClients.create("mongodb://localhost:27017/?replicaSet=rs0")) {
            MongoDatabase base = cliente.getDatabase("nosql02_despues");
            base.drop();
            MongoCollection<Document> movimientos = base.getCollection("movimientos");
            cargar(movimientos);

            Bson cuentaFecha = Indexes.compoundIndex(Indexes.ascending("cuentaId"), Indexes.descending("fecha"));
            Bson fechaCuenta = Indexes.compoundIndex(Indexes.descending("fecha"), Indexes.ascending("cuentaId"));
            Bson cuentaTipoFecha = Indexes.compoundIndex(Indexes.ascending("cuentaId"), Indexes.ascending("tipo"),
                    Indexes.descending("fecha"));
            movimientos.createIndex(cuentaFecha);

            System.out.println("consulta                         | plan                     |  claves | documentos | devueltos |  ms");

            Bson deLaCuenta = eq("cuentaId", "cuenta-500");
            Bson recientes = Sorts.descending("fecha");
            mostrar("extracto: 20 más recientes", movimientos.find(deLaCuenta).sort(recientes).limit(20));

            movimientos.createIndex(fechaCuenta);                // los mismos campos, en el orden equivocado
            mostrar("  ...con {fecha, cuentaId}", movimientos.find(deLaCuenta).sort(recientes).limit(20).hint(fechaCuenta));
            movimientos.dropIndex(fechaCuenta);

            Bson deUnPeriodo = and(deLaCuenta, gte("fecha", new Date(INICIO + 100_000L * 60_000)),
                    lt("fecha", new Date(INICIO + 150_000L * 60_000)));
            mostrar("extracto de un rango de fechas", movimientos.find(deUnPeriodo).sort(recientes));

            Bson soloRetiros = and(deLaCuenta, eq("tipo", "RETIRO"));
            mostrar("retiros: con {cuentaId, fecha}", movimientos.find(soloRetiros).sort(recientes).limit(20));
            movimientos.createIndex(cuentaTipoFecha);
            mostrar("  ...con {cuentaId, tipo, fecha}", movimientos.find(soloRetiros).sort(recientes).limit(20));

            mostrar("página 9 (skip 160)", movimientos.find(deLaCuenta).sort(recientes).skip(160).limit(20));
            Date ultimaDeLaPagina8 = ultimaFechaDeLaPagina(movimientos, deLaCuenta, 8);
            mostrar("página 9 (fecha < la última)",
                    movimientos.find(and(deLaCuenta, lt("fecha", ultimaDeLaPagina8))).sort(recientes).limit(20));

            System.out.println("Índices: " + movimientos.listIndexes().map(indice -> indice.getString("name")).into(new ArrayList<>()));
        }
    }

    /** Pagina como lo haría el cliente: cada página pide "lo anterior a la última fecha que ya vi". */
    private static Date ultimaFechaDeLaPagina(MongoCollection<Document> movimientos, Bson filtro, int pagina) {
        Date ultima = null;
        for (int i = 1; i <= pagina; i++) {
            Bson siguiente = ultima == null ? filtro : and(filtro, lt("fecha", ultima));
            List<Document> documentos = movimientos.find(siguiente).sort(Sorts.descending("fecha")).limit(20).into(new ArrayList<>());
            ultima = documentos.get(documentos.size() - 1).getDate("fecha");
        }
        return ultima;
    }

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
}
```

```text
consulta                         | plan                     |  claves | documentos | devueltos |  ms
extracto: 20 más recientes       | LIMIT<FETCH<IXSCAN       |      20 |         20 |        20 |   2
  ...con {fecha, cuentaId}       | LIMIT<FETCH<IXSCAN       |  17.393 |         20 |        20 |   6
extracto de un rango de fechas   | FETCH<IXSCAN             |      57 |         57 |        57 |   0
retiros: con {cuentaId, fecha}   | LIMIT<FETCH<IXSCAN       |      37 |         37 |        20 |   0
  ...con {cuentaId, tipo, fecha} | LIMIT<FETCH<IXSCAN       |      20 |         20 |        20 |   1
página 9 (skip 160)              | LIMIT<FETCH<SKIP<IXSCAN  |     180 |         20 |        20 |   0
página 9 (fecha < la última)     | LIMIT<FETCH<IXSCAN       |      20 |         20 |        20 |   0
Índices: [_id_, cuentaId_1_fecha_-1, cuentaId_1_tipo_1_fecha_-1]
```

| Consulta | Por qué ahora cuesta lo que devuelve |
|---|---|
| Extracto | `{cuentaId, fecha}`: igualdad y después orden. El índice lleva directo a la cuenta 500 y sus entradas ya están por fecha: 20 claves, 20 documentos |
| ...con `{fecha, cuentaId}` | Los mismos campos, al revés: vuelve a recorrer 17.393 claves. Ahora lo hace solo en el índice (carga 20 documentos), pero sigue leyendo casi todo el índice |
| Rango de fechas | La igualdad delante y el rango detrás: 57 claves para 57 movimientos |
| Retiros con `{cuentaId, fecha}` | El índice no tiene `tipo`: se cargan 37 documentos y se descartan los 17 depósitos. Es aceptable o no según la frecuencia de la consulta |
| ...con `{cuentaId, tipo, fecha}` | Dos igualdades y el orden: 20 de 20. Es un índice más, y cada escritura lo pagará |
| Página 9 con `skip` | Con el índice bueno, `skip` recorre 180 claves (y solo carga 20 documentos). Crece con el número de página |
| Página 9 por clave | 20 claves, sea la página 9 o la 9.000 |

El índice `{cuentaId, tipo, fecha}` no reemplaza a `{cuentaId, fecha}`: el extracto sin filtro de tipo no puede usarlo para ordenar, porque `tipo` está en medio. Por eso quedan los dos, y por eso cada índice se justifica con una consulta.

## Errores comunes

- Mirar solo si el plan dice `IXSCAN`.
- Crear un índice por campo en lugar de uno compuesto por consulta.
- Poner primero el campo de rango o el de orden "porque es el más importante".
- Paginar con `skip` en listas que pueden llegar lejos.
- Paginar por una clave que se repite, sin desempate: se pierden o se repiten elementos entre páginas.
- Medir con la colección vacía del entorno de desarrollo.
- Acumular índices "por si acaso" y no revisar nunca cuáles se usan.
- Crear un índice sobre una colección grande en producción sin pensar en cuánto tarda y qué consume mientras se construye.

## Taller

**Objetivo:** que cada consulta frecuente de tu servicio de pedidos examine lo que devuelve, y poder demostrarlo con `explain`.

Sobre el modelo y los datos de tu [taller 01](../01-modelar-por-consultas/).

1. Para cada consulta de tu tabla de patrones de acceso, ejecuta `explain("executionStats")` sin más índices que `_id` y anota etapas, claves, documentos y devueltos.
2. Diseña los índices con ESR. Para cada uno, escribe qué consulta lo justifica.
3. Repite el paso 1 con tus índices. Cualquier consulta frecuente con una proporción examinados/devueltos mayor que 2 necesita una explicación por escrito.
4. Implementa "mis pedidos" con paginación por clave, con desempate. Demuestra con `explain` que la página 500 cuesta lo mismo que la 1, y con datos que no se pierde ni se repite ningún pedido al recorrer todas las páginas mientras se insertan pedidos nuevos.
5. Mide el coste de escribir: inserta 100.000 pedidos con 1, 3 y 6 índices y compara los tiempos.
6. Con `$indexStats`, tras ejecutar tu carga de consultas, encuentra un índice que no se use. Elimínalo o justifícalo.
7. Escribe una consulta que ningún índice pueda resolver bien (por ejemplo, una expresión regular que empieza por `.*`) y explica por qué.
8. Consigue una consulta cubierta y muestra `totalDocsExamined: 0`.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| Cada índice está justificado por una consulta | Tabla del paso 2 |
| Las consultas frecuentes examinan lo que devuelven, o hay una razón escrita | Salidas de `explain` del paso 3 |
| La paginación es estable y de coste constante | Paso 4 |
| El coste de escritura de los índices está medido | Paso 5 |
| Ningún índice sobra sin justificación | Paso 6 |

**Entrega:** rama `taller/nosql-02`, con los programas, las salidas de `explain` y `ANALISIS.md` en `taller/solucion/`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

Los índices se crean en las migraciones del despliegue, igual que las tablas de una base relacional, y no los crea la aplicación al arrancar (Spring Data MongoDB puede hacerlo con la creación automática de índices, que viene apagada por defecto y conviene dejar así fuera de desarrollo). En colecciones grandes, crear un índice consume recursos durante minutos u horas: se planifica.

Para encontrar consultas malas en producción: el perfilador de MongoDB con un umbral (`db.setProfilingLevel(1, {slowms: 50})`), el registro de consultas lentas, o el asesor de rendimiento del servicio gestionado si lo usas. La pregunta de cada revisión es la misma de esta sesión: cuánto examinó para lo que devolvió.

## Preguntas senior

1. ¿Por qué una consulta que usa un índice puede ser tan lenta como una que no lo usa?
2. Explica la regla ESR con un ejemplo y di qué pasa si inviertes el orden.
3. ¿Por qué `skip` no escala y qué se hace en su lugar? ¿Qué necesita la clave de paginación?
4. ¿Cuándo prefieres un índice que no cubre un filtro y aceptas descartar algunos documentos?
5. ¿Qué cuesta un índice y cómo decides retirarlo?
6. ¿Qué es una consulta cubierta y cuándo merece la pena buscarla?
