# 06 — MongoDB reactivo y flujos de cambios

> Cómo modelar documentos (incrustar o referenciar, índices, esquemas flexibles) está en el módulo [nosql](../../nosql/), con el driver bloqueante. Esta sesión supone que ya tienes un modelo y trata otra cosa: hablar con MongoDB sin bloquear y **enterarte de lo que cambia** sin preguntar todo el tiempo.

## Idea

Esperas un paquete. Puedes asomarte a la puerta cada minuto para ver si llegó: te cansas, y aun así lo encuentras con un retraso de hasta un minuto. O puedes poner un timbre: no haces nada hasta que suena, y suena en cuanto llega. Si además el repartidor deja una nota numerada ("paquete 42"), cuando vuelvas de viaje puedes pedir "todo lo que llegó después del 42" y no perderte nada.

Asomarse es el **sondeo**. El timbre es el **flujo de cambios**. La nota numerada es la **ficha de reanudación**.

## Lo esencial

### El driver reactivo

MongoDB tiene un driver Java oficial con API de Reactive Streams (`mongodb-driver-reactivestreams`), y Spring Data MongoDB lo envuelve en `ReactiveMongoTemplate` y `ReactiveMongoRepository`. Con Spring Boot 4 el iniciador es `spring-boot-starter-data-mongodb-reactive`, y la conexión se configura en `spring.mongodb.uri` (antes era `spring.data.mongodb.uri`).

| Bloqueante | Reactivo |
|---|---|
| `MongoTemplate.find(...)`: `List<T>` | `ReactiveMongoTemplate.find(...)`: `Flux<T>` |
| `MongoRepository<T, ID>` | `ReactiveMongoRepository<T, ID>` |
| `findById`: `Optional<T>` | `findById`: `Mono<T>` |

Todo lo de las sesiones 02 y 03 vale igual: nada pasa sin suscribirse, y un `Flux` de un `find` respeta la contrapresión (el driver pide lotes al servidor según la demanda).

### Flujos de cambios

Un **flujo de cambios** (*change stream*) es un cursor que MongoDB mantiene abierto sobre su registro de operaciones (el *oplog*) y por el que envía cada inserción, actualización o borrado de una colección, en orden, en cuanto se confirma.

| | Sondeo | Flujo de cambios |
|---|---|---|
| Latencia | Hasta el intervalo de sondeo | Milisegundos |
| Consultas sin cambios | Una por intervalo, por cada instancia que sondea | Ninguna nueva: un cursor abierto que el driver mantiene vivo |
| Cambios que se pierden | Los que tengan la misma marca de tiempo que el último visto, los borrados, los que ocurran durante una caída si el filtro no es perfecto | Ninguno, si guardas la ficha y la reanudas dentro de la ventana del oplog |
| Requisitos | Ninguno | Conjunto de réplicas (o clúster fragmentado). Un nodo solo, arrancado como réplica, basta |

Cada evento trae una **ficha de reanudación** (*resume token*). Si el proceso se cae, al volver pide el flujo con `resumeAfter(ficha)` y MongoDB le entrega todo lo que pasó desde entonces, siempre que siga en el oplog (su tamaño marca cuánto tiempo puedes estar caído).

Garantía: **al menos una vez**. Si guardas la ficha después de procesar el evento y te caes justo entre las dos cosas, al reanudar recibirás ese evento otra vez. El consumidor tiene que ser idempotente, como en [Kafka](../../kafka-y-sagas/kafka/04-entrega-e-idempotencia/).

## Ejemplo

Un notificador avisa de cada movimiento nuevo de la colección `movimientos`. Un programa de Spring Boot sin servidor web escribe cinco movimientos, uno cada 700 ms, y mide cuánto tarda el notificador en enterarse de cada uno. Un `CommandListener` del driver cuenta los comandos que llegan a MongoDB: así el costo se mide, no se supone.

Usa el MongoDB del [entorno](../entorno/) (puerto 27018, conjunto de réplicas `rs0` de un nodo). Se ejecuta con `mvn -q spring-boot:run` desde cada carpeta.

<!-- file: ejemplo/despues/src/main/resources/application.yaml -->
```yaml
spring:
  mongodb:
    # directConnection: el conjunto de réplicas se anuncia como localhost:27017, que desde tu equipo es otro puerto
    uri: mongodb://localhost:27018/banco?directConnection=true
  main:
    banner-mode: off

logging:
  level:
    root: warn
```

<!-- file: ejemplo/antes/src/main/java/banco/avisos/ContadorDeComandos.java -->
```java
package banco.avisos;

import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.stereotype.Component;

/** Cuenta cada comando que el driver envía a MongoDB: así se mide el costo de enterarse, no se supone. */
@Component
public class ContadorDeComandos implements CommandListener, MongoClientSettingsBuilderCustomizer {

    private final Map<String, AtomicInteger> porNombre = new ConcurrentHashMap<>();

    @Override
    public void customize(com.mongodb.MongoClientSettings.Builder configuracion) {
        configuracion.addCommandListener(this);
    }

    @Override
    public void commandStarted(CommandStartedEvent evento) {
        String nombre = evento.getCommandName();
        boolean sobreMovimientos = evento.getCommand().containsKey(nombre)
                && evento.getCommand().get(nombre).isString()
                && evento.getCommand().getString(nombre).getValue().equals("movimientos");     // find, insert, aggregate...
        boolean continuacion = nombre.equals("getMore")
                && evento.getCommand().getString("collection").getValue().equals("movimientos");   // pedir más a un cursor
        if (sobreMovimientos || continuacion) {
            porNombre.computeIfAbsent(nombre, clave -> new AtomicInteger()).incrementAndGet();
        }
    }

    public void reiniciar() {
        porNombre.clear();
    }

    public Map<String, Integer> resumen() {
        Map<String, Integer> copia = new TreeMap<>();
        porNombre.forEach((nombre, cuenta) -> copia.put(nombre, cuenta.get()));
        return copia;
    }
}
```

### Antes

<!-- file: ejemplo/antes/src/main/java/banco/avisos/NotificadorPorSondeo.java -->
```java
package banco.avisos;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/** "¿Hay algo nuevo?" cada segundo. Reactivo por fuera, pero sigue siendo preguntar una y otra vez. */
@Component
public class NotificadorPorSondeo {

    static final Duration CADA = Duration.ofSeconds(1);

    private final ReactiveMongoTemplate mongo;

    public NotificadorPorSondeo(ReactiveMongoTemplate mongo) {
        this.mongo = mongo;
    }

    public Flux<Movimiento> avisos() {
        AtomicReference<Instant> ultimoVisto = new AtomicReference<>(Instant.now());
        return Flux.interval(CADA)
                .concatMap(tick -> mongo.find(query(where("fecha").gt(ultimoVisto.get())).with(Sort.by("fecha")), Movimiento.class)
                        .doOnNext(movimiento -> ultimoVisto.set(movimiento.fecha())));
    }
}
```

<!-- file: ejemplo/antes/src/main/java/banco/avisos/Aplicacion.java -->
```java
package banco.avisos;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.core.Disposable;

@SpringBootApplication
public class Aplicacion {

    public static void main(String[] args) {
        SpringApplication.run(Aplicacion.class, args);
    }

    /** Escribe cinco movimientos, uno cada 700 ms, y mide cuánto tarda el notificador en enterarse de cada uno. */
    @Bean
    CommandLineRunner demostracion(ReactiveMongoTemplate mongo, Escritor escritor, NotificadorPorSondeo notificador,
                                   ContadorDeComandos comandos) {
        return args -> {
            mongo.dropCollection(Movimiento.class).then(mongo.createCollection(Movimiento.class)).block();
            List<Long> latencias = new CopyOnWriteArrayList<>();
            comandos.reiniciar();

            Disposable suscripcion = notificador.avisos()
                    .subscribe(movimiento -> latencias.add(Duration.between(movimiento.fecha(), Instant.now()).toMillis()));
            for (int i = 1; i <= 5; i++) {
                Thread.sleep(700);                                // el hilo main no es un bucle de eventos: puede dormir
                escritor.registrar("cuenta-" + i, 10_000L * i).block();
            }
            Thread.sleep(2_000);                                  // margen para que llegue el último aviso
            suscripcion.dispose();

            System.out.println("Sondeo cada " + NotificadorPorSondeo.CADA.toMillis() + " ms");
            System.out.println("Avisos recibidos:        " + latencias.size() + " de 5");
            System.out.println("Latencia de cada aviso:  " + latencias + " ms");
            System.out.println("Latencia media:          " + latencias.stream().mapToLong(Long::longValue).average().orElse(0) + " ms");
            System.out.println("Comandos a MongoDB:      " + comandos.resumen());
        };
    }
}
```

```
Sondeo cada 1000 ms
Avisos recibidos:        5 de 5
Latencia de cada aviso:  [318, 572, 864, 156, 455] ms
Latencia media:          473.0 ms
Comandos a MongoDB:      {find=5, insert=5}
```

### Análisis

| Lo que se ve | Por qué |
|---|---|
| Latencias entre 150 y 870 ms, media cercana a medio segundo | Cada movimiento espera al siguiente sondeo. La media tiende a la mitad del intervalo |
| Un `find` por segundo | Haya o no movimientos. Son 86.400 consultas al día **por instancia** del notificador; con tres instancias y diez colecciones vigiladas, 2,6 millones de consultas que casi siempre responden "nada" |
| Bajar la latencia cuesta consultas | Sondear cada 100 ms da una latencia media de 50 ms y diez veces más consultas |
| Es "reactivo" | `Flux.interval` y `ReactiveMongoTemplate` no bloquean ningún hilo, pero el diseño sigue siendo preguntar una y otra vez. Reactivo no es lo mismo que dirigido por eventos |
| El filtro `fecha > últimoVisto` es frágil | Dos movimientos con la misma fecha al milisegundo y el segundo se pierde; un reloj desajustado entre escritores, igual; un borrado no se ve nunca |

### Después

<!-- file: ejemplo/despues/src/main/java/banco/avisos/NotificadorPorFlujoDeCambios.java -->
```java
package banco.avisos;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import org.bson.BsonValue;
import org.springframework.data.mongodb.core.ChangeStreamEvent;
import org.springframework.data.mongodb.core.ChangeStreamOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * MongoDB avisa cuando hay algo nuevo: un flujo de cambios es un cursor abierto sobre el registro de operaciones.
 * Cada evento trae una ficha de reanudación: guardándola, se puede seguir donde se quedó tras una caída.
 */
@Component
public class NotificadorPorFlujoDeCambios {

    private final ReactiveMongoTemplate mongo;
    private volatile BsonValue ultimaFicha;

    public NotificadorPorFlujoDeCambios(ReactiveMongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** Si hay una ficha guardada, reanuda justo después de ella; si no, empieza en este momento. */
    public Flux<Movimiento> avisos() {
        ChangeStreamOptions.ChangeStreamOptionsBuilder opciones = ChangeStreamOptions.builder()
                .filter(newAggregation(match(where("operationType").is("insert"))));    // solo altas
        if (ultimaFicha != null) {
            opciones.resumeAfter(ultimaFicha);
        }
        return mongo.changeStream("movimientos", opciones.build(), Movimiento.class)
                .doOnNext(evento -> ultimaFicha = evento.getResumeToken())   // en producción, se guarda en la base
                .mapNotNull(ChangeStreamEvent::getBody);
    }
}
```

<!-- file: ejemplo/despues/src/main/java/banco/avisos/Aplicacion.java -->
```java
package banco.avisos;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.core.Disposable;

@SpringBootApplication
public class Aplicacion {

    public static void main(String[] args) {
        SpringApplication.run(Aplicacion.class, args);
    }

    @Bean
    CommandLineRunner demostracion(ReactiveMongoTemplate mongo, Escritor escritor, NotificadorPorFlujoDeCambios notificador,
                                   ContadorDeComandos comandos) {
        return args -> {
            mongo.dropCollection(Movimiento.class).then(mongo.createCollection(Movimiento.class)).block();
            List<Long> latencias = new CopyOnWriteArrayList<>();
            comandos.reiniciar();

            // 1. La misma medición que con sondeo
            Disposable suscripcion = notificador.avisos()
                    .subscribe(movimiento -> latencias.add(Duration.between(movimiento.fecha(), Instant.now()).toMillis()));
            Thread.sleep(1_000);                                  // el cursor del flujo se abre de forma asíncrona
            for (int i = 1; i <= 5; i++) {
                Thread.sleep(700);
                escritor.registrar("cuenta-" + i, 10_000L * i).block();
            }
            Thread.sleep(2_000);
            suscripcion.dispose();

            System.out.println("Flujo de cambios");
            System.out.println("Avisos recibidos:        " + latencias.size() + " de 5");
            System.out.println("Latencia de cada aviso:  " + latencias + " ms");
            System.out.println("Latencia media:          " + latencias.stream().mapToLong(Long::longValue).average().orElse(0) + " ms");
            System.out.println("Comandos a MongoDB:      " + comandos.resumen());

            // 2. Una caída: el notificador se detiene, llegan dos movimientos, y vuelve con la última ficha
            escritor.registrar("cuenta-6", 60_000).block();
            escritor.registrar("cuenta-7", 70_000).block();
            List<String> recuperados = notificador.avisos()
                    .take(2)
                    .map(Movimiento::cuentaId)
                    .collectList()
                    .block(Duration.ofSeconds(5));
            System.out.println();
            System.out.println("Escritos mientras el notificador estaba caído: cuenta-6, cuenta-7");
            System.out.println("Recibidos al reanudar con la última ficha:     " + recuperados);
        };
    }
}
```

```
Flujo de cambios
Avisos recibidos:        5 de 5
Latencia de cada aviso:  [20, 4, 4, 4, 4] ms
Latencia media:          7.2 ms
Comandos a MongoDB:      {aggregate=1, getMore=8, insert=5}

Escritos mientras el notificador estaba caído: cuenta-6, cuenta-7
Recibidos al reanudar con la última ficha:     [cuenta-6, cuenta-7]
```

- **Latencia de milisegundos**: el aviso sale cuando MongoDB confirma la inserción.
- **Un `aggregate`** abre el flujo (es una agregación con la etapa `$changeStream`) y los **`getMore`** lo mantienen: el servidor retiene cada uno hasta que hay cambios o vence una espera máxima (un segundo por defecto). No hay consultas a la colección.
- **Reanudación**: el segundo bloque detiene el notificador, escribe dos movimientos y lo vuelve a abrir con la última ficha. Recibe exactamente los dos que se perdió. En producción la ficha se guarda en la base, junto con el efecto de procesar el evento.

Una nota honesta sobre `Thread.sleep(1_000)` tras suscribirse: el cursor del flujo se abre de forma asíncrona, y sin esa pausa el primer movimiento podría escribirse antes. En un servicio real no hace falta, porque el notificador arranca antes de que nadie escriba; si lo necesitas exacto, `startAtOperationTime` fija desde cuándo se quieren los cambios.

## Errores comunes

- Intentar abrir un flujo de cambios contra un MongoDB que no es un conjunto de réplicas. Falla con `The $changeStream stage is only supported on replica sets or mongos` (`Location40573`, MongoDB 9.0.8).
- Conectar desde tu equipo a un conjunto de réplicas en Docker sin `directConnection=true`: el driver descubre los miembros por el nombre con que se anuncian (aquí `localhost:27017`), que desde fuera es otro puerto.
- No guardar la ficha de reanudación, o guardarla **antes** de procesar el evento (y perder eventos en una caída).
- Suponer "exactamente una vez". Es al menos una vez: procesa de forma idempotente.
- Un oplog pequeño y una caída larga: la ficha ya no está en el oplog y la reanudación falla (`ChangeStreamHistoryLost`, código 286: `Resume of change stream was not possible, as the resume point may no longer be in the oplog`). Una ficha cuya marca de tiempo sigue en el oplog pero que no corresponde a ningún evento (por ejemplo, con otro `documentKey`) da otro error (`ChangeStreamFatalError`, 280: `cannot resume stream; the resume token was not found`). Hay que decidir qué hacer entonces: reconstruir desde la colección.
- Sondeo "reactivo" con `Flux.interval`: sigue siendo sondeo.
- Filtrar en el cliente lo que se puede filtrar en el servidor: el `match` sobre `operationType` (o sobre campos del documento) viaja en el `aggregate` y MongoDB no te envía lo que no quieres.

## Taller

**Objetivo:** notificar cambios de estado de tus pedidos con un flujo de cambios, sin perder eventos en una caída y sin procesar dos veces.

Guarda tus pedidos en MongoDB (si hiciste el módulo [nosql](../../nosql/), reutiliza tu modelo de documentos) con el driver reactivo.

1. Un proceso "confirmador" cambia el estado de pedidos (`PENDIENTE` → `CONFIRMADO` o `RECHAZADO`) de forma continua, varias veces por segundo.
2. Un notificador abre un flujo de cambios filtrado **en el servidor** para recibir solo cambios del campo `estado`. Por cada uno, inserta un aviso en una colección `avisos`.
3. Guarda la ficha de reanudación en MongoDB. Decide en qué orden guardas el aviso y la ficha, y por qué.
4. Detén el notificador (Ctrl+C) durante diez segundos con el confirmador funcionando. Vuelve a arrancarlo. Comprueba que hay exactamente un aviso por cambio de estado: ni faltan ni sobran.
5. Mata el notificador con `kill -9` justo después de insertar un aviso (pista: un punto de parada o una salida forzada por configuración). Al reanudar, ¿se duplica? Haz el aviso idempotente (pista: un índice único sobre el pedido y el estado) y repite.
6. Mide la latencia media desde el cambio de estado hasta el aviso, y compárala con una versión por sondeo cada 500 ms. Cuenta los comandos en las dos.
7. Exponlo como SSE en un servicio WebFlux: `GET /pedidos/{id}/estados`.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| Un aviso por cambio, tras una detención ordenada | Conteos del paso 4 |
| Sin duplicados tras una caída brusca | Paso 5, con el índice único |
| El filtro se aplica en el servidor | El `aggregate` registrado lleva el `$match` |
| La comparación con sondeo tiene números | Paso 6 |

**Entrega:** rama `taller/webflux-06`, con el código en `taller/solucion/` y `ANALISIS.md`.

## En el trabajo real

Los flujos de cambios se usan para invalidar cachés, alimentar índices de búsqueda, sincronizar con otros sistemas y empujar notificaciones a clientes. Para lo último, un servicio WebFlux con SSE encima de un flujo de cambios es una combinación natural. Para llevar cambios a muchos sistemas, lo habitual es un conector (Debezium o el conector de MongoDB para Kafka) que publica en Kafka y deja que cada consumidor lea a su ritmo: el flujo de cambios pasa a ser la fuente de un registro de eventos, con las mismas reglas de idempotencia. El tamaño del oplog (`replSetResizeOplog`) es una decisión de operación: tiene que cubrir la caída más larga que estés dispuesto a recuperar sin reconstruir.

## Preguntas senior

1. ¿Por qué un sondeo con `Flux.interval` sobre un driver reactivo no es un diseño dirigido por eventos?
2. ¿Qué garantía de entrega da un flujo de cambios con reanudación, y qué exige eso al consumidor?
3. ¿En qué orden guardas la ficha de reanudación y el efecto de procesar el evento? ¿Qué pasa en cada orden si te caes en medio?
4. ¿Qué limita cuánto tiempo puede estar caído un consumidor de flujos de cambios?
5. ¿Por qué un flujo de cambios necesita un conjunto de réplicas?
6. ¿Cuándo pondrías Kafka entre MongoDB y los consumidores en vez de que cada uno abra su flujo?
