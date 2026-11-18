# 03 — Contrapresión, schedulers y el error clásico: bloquear el bucle de eventos

## Idea

**Contrapresión.** Una cinta de equipaje que trae maletas más rápido de lo que la gente las recoge. Si la cinta no se entera, las maletas se amontonan al final hasta caer al suelo. Las cintas buenas tienen un sensor: si el final está lleno, se detienen. Y si la cinta no puede detenerse (un río no se detiene), hay que decidir qué hacer con lo que sobra.

**El bucle de eventos.** Una centralita con cuatro telefonistas que atienden a toda la empresa. Funciona porque cada llamada dura segundos: "le paso". Si una telefonista se pone a buscar un expediente en el archivo mientras sostiene la llamada, sus líneas quedan sin atender, aunque las otras tres estén libres. No hace falta que se caiga la centralita: basta con que alguien haga allí un trabajo que no le toca.

## Lo esencial

### Contrapresión

En Reactive Streams el consumidor **pide**: `request(n)` significa "puedo con n más". El productor no debe enviar más de lo pedido. Así un consumidor lento frena a un productor rápido sin perder datos y sin colas infinitas en memoria.

Funciona solo si la fuente **puede esperar**. Un `Flux.range`, una consulta a la base o la lectura de un archivo pueden esperar: producen cuando se les pide. Pero hay fuentes que no: un temporizador (`Flux.interval`), eventos de un sensor, cotizaciones, clics. Para esas, tienes que decidir qué pasa con el exceso:

| Estrategia | Qué hace | Cuándo |
|---|---|---|
| `onBackpressureBuffer(n)` | Guarda hasta n elementos; si se llena, error | Picos cortos; no se puede perder nada |
| `onBackpressureDrop(alDescartar)` | Descarta lo que llega sin demanda | Solo importa lo reciente y perder no es grave |
| `onBackpressureLatest()` | Se queda solo con el último | Un saldo en pantalla, un indicador |
| `sample(Duration)` | Toma un valor cada intervalo | Gráficas, telemetría |
| Ninguna | `OverflowException` o un error parecido | Nunca a propósito |

Elegir es una decisión **de negocio**: ¿se puede perder una cotización? Sí. ¿Un movimiento bancario? No: entonces la fuente tiene que poder esperar (un registro de eventos como Kafka, una tabla), no un temporizador.

### Schedulers: quién ejecuta cada paso

Reactor no crea hilos por su cuenta: cada paso se ejecuta en el hilo que emitió la señal, salvo que digas otra cosa con un **scheduler**.

| Scheduler | Hilos | Para qué |
|---|---|---|
| `Schedulers.parallel()` | Uno por núcleo | Trabajo de CPU corto. **No se bloquea** |
| `Schedulers.boundedElastic()` | Crecen según haga falta, con un tope (10 por núcleo) y una cola | **Envolver llamadas bloqueantes** que no puedes evitar |
| `Schedulers.single()` | Uno | Tareas en orden estricto |
| Bucle de eventos de Netty (`reactor-http-nio-N`) | Uno por núcleo | Atender E/S de red. **No se bloquea nunca** |

| Operador | Qué cambia |
|---|---|
| `subscribeOn(s)` | En qué hilo empieza la suscripción, es decir, dónde se ejecuta la fuente. Da igual dónde lo pongas en la cadena |
| `publishOn(s)` | En qué hilo se ejecuta **lo que viene después** en la cadena |

El patrón para una llamada bloqueante inevitable:

```java
Mono.fromCallable(() -> jdbc.consultar(...))          // perezoso: no se ejecuta al ensamblar
    .subscribeOn(Schedulers.boundedElastic())         // se ejecuta en un hilo hecho para esperar
```

Es un parche, no una solución: cada llamada sigue ocupando un hilo mientras espera, y `boundedElastic` tiene un tope. Si tu servicio entero es así, los [hilos virtuales con MVC](../01-por-que-reactivo/) son más simples. La solución de verdad es un driver no bloqueante (sesiones 05 y 06).

### BlockHound

Un bloqueo en el bucle no da error: da latencia, y solo con carga. [BlockHound](https://github.com/reactor/BlockHound) instrumenta la JVM y lanza `BlockingOperationError` cuando un hilo marcado como **no bloqueante** (los de `parallel`, `single`, y los del bucle de Netty) llama a algo bloqueante: `Thread.sleep`, lectura de sockets o archivos, `Object.wait`, `LockSupport.park`...

- Se instala con `BlockHound.install()` al arrancar.
- Desde el JDK 13 necesita la opción de la JVM `-XX:+AllowRedefinitionToAddDeleteMethods` (la JVM avisa de que está obsoleta; sigue haciendo falta) y, desde el JDK 21, `-XX:+EnableDynamicAgentLoading` para no ver una advertencia.
- No instrumenta el propio Maven: los programas de esta sesión se ejecutan en un JVM aparte con `exec:exec`.
- Va en **pruebas y entornos de preproducción**, no en producción: añade sobrecarga.

## Ejemplo

Dos problemas en un programa. Primero, una pantalla que pinta saldos que cambian mil veces por segundo. Segundo, un "servidor" con un bucle de dos hilos que recibe ocho consultas de saldo (JDBC, 200 ms cada una) y, justo después, un ping que no hace nada. Java puro con Reactor y BlockHound.

### Antes

<!-- file: ejemplo/antes/src/main/java/Main.java -->
```java
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import reactor.blockhound.BlockHound;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/** Dos errores que funcionan "casi siempre": un productor que ignora la demanda y JDBC dentro del bucle de eventos. */
public class Main {

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--blockhound")) {
            BlockHound.install();                                 // a partir de aquí, bloquear en un hilo no bloqueante es un error
            System.out.println("BlockHound instalado");
        }
        contrapresion();
        System.out.println();
        bloqueoEnElBucle();
    }

    // ---- 1. Contrapresión: el saldo cambia 1000 veces por segundo y la pantalla pinta 100 ----

    static void contrapresion() {
        Scheduler pantalla = Schedulers.fromExecutorService(Executors.newSingleThreadExecutor(), "pantalla");
        AtomicInteger pintados = new AtomicInteger();
        long inicio = System.nanoTime();
        try {
            Flux.interval(Duration.ofMillis(1))                   // un saldo nuevo cada milisegundo
                    .publishOn(pantalla)                          // la pantalla pide de 256 en 256
                    .doOnNext(saldo -> {
                        pintar();
                        pintados.incrementAndGet();
                    })
                    .blockLast();                                 // la pantalla está siempre abierta
            System.out.println("1. Contrapresión: terminó sin error, pintados " + pintados.get());
        } catch (RuntimeException error) {
            System.out.printf("1. Contrapresión: falló a los %d ms, tras pintar %d saldos%n", desde(inicio), pintados.get());
            System.out.println("   " + error.getMessage());
        } finally {
            pantalla.dispose();
        }
    }

    /** Pintar cuesta 10 ms. */
    static void pintar() {
        dormir(10);
    }

    // ---- 2. JDBC dentro del bucle de eventos ----

    /** Como el bucle de eventos de Netty: muy pocos hilos, que atienden a todos y nunca deberían bloquearse. */
    static final Scheduler BUCLE = Schedulers.newParallel("bucle", 2);

    static void bloqueoEnElBucle() {
        long inicio = System.nanoTime();
        AtomicLong esperaDelPing = new AtomicLong();

        Flux<String> saldos = Flux.range(1, 8)                    // ocho peticiones de saldo a la vez
                .flatMap(i -> Mono.defer(() -> consultarSaldo(i)).subscribeOn(BUCLE));
        Mono<String> ping = Mono.delay(Duration.ofMillis(10))     // y un ping, 10 ms después, que no toca la base
                .flatMap(tick -> {
                    long enviado = System.nanoTime();
                    return Mono.fromSupplier(() -> "pong").subscribeOn(BUCLE)
                            .doOnNext(pong -> esperaDelPing.set(desde(enviado)));
                });

        try {
            Flux.merge(saldos, ping).blockLast();
            System.out.printf("2. Ocho consultas de 200 ms con un bucle de 2 hilos: %d ms en total%n", desde(inicio));
            System.out.printf("   El ping, que no hace nada, esperó %d ms%n", esperaDelPing.get());
        } catch (Error | RuntimeException error) {
            System.out.println("2. Falló: " + error);
        } finally {
            BUCLE.dispose();
        }
    }

    /** El manejador de la petición. Llama a JDBC directamente: el hilo del bucle se queda esperando a la base. */
    static Mono<String> consultarSaldo(int i) {
        return Mono.just(consultarSaldoConJdbc("cuenta-" + i));
    }

    /** Hace de consulta JDBC: bloquea el hilo 200 ms. */
    static String consultarSaldoConJdbc(String cuenta) {
        dormir(200);
        return cuenta + ": 1000000";
    }

    // ---- utilidades ----

    static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupcion);
        }
    }

    static long desde(long inicioNanos) {
        return (System.nanoTime() - inicioNanos) / 1_000_000;
    }
}
```

```bash
cd ejemplo/antes
mvn -q compile exec:exec
```

```
1. Contrapresión: falló a los 3015 ms, tras pintar 256 saldos
   Could not emit tick 256 due to lack of requests (interval doesn't support small downstream requests that replenish slower than the ticks)

2. Ocho consultas de 200 ms con un bucle de 2 hilos: 815 ms en total
   El ping, que no hace nada, esperó 800 ms
```

Con BlockHound instalado (`mvn -q exec:exec@con-blockhound`, ver el `pom.xml`):

```
BlockHound instalado
1. Contrapresión: falló a los 2823 ms, tras pintar 256 saldos
   Could not emit tick 256 due to lack of requests (interval doesn't support small downstream requests that replenish slower than the ticks)

[ERROR] (bucle-2) Operator called default onErrorDropped - reactor.blockhound.BlockingOperationError: Blocking call! java.lang.Thread.sleep0
2. Falló: reactor.core.Exceptions$ReactiveException: reactor.blockhound.BlockingOperationError: Blocking call! java.lang.Thread.sleep0
reactor.blockhound.BlockingOperationError: Blocking call! java.lang.Thread.sleep0
	at java.base/java.lang.Thread.sleep0(Thread.java)
	at java.base/java.lang.Thread.sleep(Thread.java:509)
	at Main.dormir(Main.java:96)
	at Main.consultarSaldoConJdbc(Main.java:88)
	at Main.consultarSaldo(Main.java:83)
	...
```

La pantalla no la señala: su hilo sale de un `Executor` normal, no marcado como no bloqueante, y dormir ahí es legítimo. Su problema es otro.

### Análisis

| Lo que se ve | Por qué |
|---|---|
| La pantalla falla tras 256 saldos | `publishOn` pide 256 elementos de golpe (su *prefetch*) y repone de a poco. `Flux.interval` no puede esperar: al tick 256, unos 256 ms después de empezar, no hay demanda y emite un error. El error llega a quien escucha unos tres segundos más tarde, cuando la pantalla termina de pintar lo que tenía en cola |
| Ocho consultas de 200 ms tardan 800 ms con dos hilos | Cada hilo del bucle ejecuta cuatro consultas, una tras otra, **dormido** en cada una |
| El ping esperó 800 ms | No toca la base, pero le tocó un hilo del bucle que estaba durmiendo dentro de JDBC. Una petición sin relación con la base tarda lo que tarda la base |
| `Mono.just(consultarSaldoConJdbc(...))` parece reactivo | Devuelve un `Mono`, pero la consulta se ejecutó entera, en el hilo del bucle, antes de que existiera el `Mono` |
| BlockHound lo señala con la línea exacta | El hilo `bucle-N` es de `Schedulers.newParallel`, marcado como no bloqueante. `Thread.sleep` ahí es un error |

En un servidor real, el ping es tu *health check*. Con carga y una consulta lenta, el orquestador lo ve tardar, da el servicio por muerto y lo reinicia: el bloqueo de un endpoint tumba todos.

### Después

<!-- file: ejemplo/despues/src/main/java/Main.java -->
```java
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import reactor.blockhound.BlockHound;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/** Los dos errores corregidos: una estrategia explícita para el exceso y la llamada bloqueante fuera del bucle. */
public class Main {

    public static void main(String[] args) {
        BlockHound.install();                                     // siempre: si algo bloquea donde no debe, que reviente
        System.out.println("BlockHound instalado");
        Schedulers.boundedElastic();                              // se crea aquí, no por primera vez desde dos hilos del bucle a la vez
        contrapresion();
        System.out.println();
        bloqueoEnElBucle();
    }

    // ---- 1. Contrapresión: la pantalla solo necesita el saldo más reciente ----

    static void contrapresion() {
        Scheduler pantalla = Schedulers.fromExecutorService(Executors.newSingleThreadExecutor(), "pantalla");
        AtomicInteger pintados = new AtomicInteger();
        AtomicInteger descartados = new AtomicInteger();
        try {
            Flux.interval(Duration.ofMillis(1))
                    .onBackpressureDrop(saldo -> descartados.incrementAndGet())   // sin demanda, el saldo viejo se tira
                    .publishOn(pantalla, 1)                       // la pantalla pide de uno en uno
                    .doOnNext(saldo -> {
                        pintar();
                        pintados.incrementAndGet();
                    })
                    .take(Duration.ofSeconds(3))
                    .blockLast();
            System.out.printf("1. Contrapresión: 3 s sin error. Pintados %d, descartados %d%n", pintados.get(), descartados.get());
        } finally {
            pantalla.dispose();
        }
    }

    static void pintar() {
        dormir(10);
    }

    // ---- 2. La consulta bloqueante, fuera del bucle de eventos ----

    static final Scheduler BUCLE = Schedulers.newParallel("bucle", 2);

    static void bloqueoEnElBucle() {
        long inicio = System.nanoTime();
        AtomicLong esperaDelPing = new AtomicLong();

        Flux<String> saldos = Flux.range(1, 8)
                .flatMap(i -> Mono.defer(() -> consultarSaldo(i)).subscribeOn(BUCLE));
        Mono<String> ping = Mono.delay(Duration.ofMillis(10))
                .flatMap(tick -> {
                    long enviado = System.nanoTime();
                    return Mono.fromSupplier(() -> "pong").subscribeOn(BUCLE)
                            .doOnNext(pong -> esperaDelPing.set(desde(enviado)));
                });

        Flux.merge(saldos, ping).blockLast();
        System.out.printf("2. Ocho consultas de 200 ms con un bucle de 2 hilos: %d ms en total%n", desde(inicio));
        System.out.printf("   El ping esperó %d ms%n", esperaDelPing.get());
        BUCLE.dispose();
    }

    /** El manejador. La llamada bloqueante va envuelta y se ejecuta en boundedElastic, un pool hecho para esperar. */
    static Mono<String> consultarSaldo(int i) {
        return Mono.fromCallable(() -> consultarSaldoConJdbc("cuenta-" + i))
                .subscribeOn(Schedulers.boundedElastic());
    }

    static String consultarSaldoConJdbc(String cuenta) {
        dormir(200);
        return cuenta + ": 1000000";
    }

    // ---- utilidades ----

    static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupcion);
        }
    }

    static long desde(long inicioNanos) {
        return (System.nanoTime() - inicioNanos) / 1_000_000;
    }
}
```

```bash
cd ejemplo/despues
mvn -q compile exec:exec      # BlockHound instalado siempre
mvn test                      # la prueba con BlockHound
```

```
BlockHound instalado
1. Contrapresión: 3 s sin error. Pintados 242, descartados 2757

2. Ocho consultas de 200 ms con un bucle de 2 hilos: 236 ms en total
   El ping esperó 13 ms
```

- **Contrapresión explícita.** La pantalla pide de uno en uno y, mientras pinta, los saldos que llegan se descartan y se cuentan. Para un saldo en pantalla está bien: el siguiente que pinta es el más reciente. Para movimientos bancarios no lo estaría.
- **La consulta bloqueante fuera del bucle.** `fromCallable` la hace perezosa y `subscribeOn(boundedElastic())` la ejecuta en un hilo hecho para esperar. El bucle solo reparte trabajo: el ping responde enseguida y las ocho consultas corren en paralelo. La línea `Schedulers.boundedElastic();` de `main` no es decoración: si dos hilos del bucle lo crean por primera vez a la vez, uno cierra su copia sobrante y BlockHound señala ese cierre como una llamada bloqueante (`Blocking call! jdk.internal.misc.Unsafe#park`) en una ejecución de vez en cuando. Se vio al ejecutar el ejemplo muchas veces.
- **BlockHound en las pruebas**, para que un bloqueo nuevo rompa la construcción:

<!-- file: ejemplo/despues/src/test/java/BloqueoTest.java -->
```java
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.blockhound.BlockHound;
import reactor.blockhound.BlockingOperationError;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

/** BlockHound en las pruebas: así un bloqueo en el bucle de eventos rompe la construcción, no la producción. */
class BloqueoTest {

    @BeforeAll
    static void instalarBlockHound() {
        BlockHound.install();
    }

    @Test
    void elManejadorNoBloqueaElHiloQueLoAtiende() {
        StepVerifier.create(Mono.defer(() -> Main.consultarSaldo(1)).subscribeOn(Schedulers.parallel()))
                .expectNext("cuenta-1: 1000000")
                .verifyComplete();
    }

    @Test
    void llamarAJdbcDesdeUnHiloNoBloqueanteSeDetecta() {
        StepVerifier.create(Mono.fromCallable(() -> Main.consultarSaldoConJdbc("cuenta-1")).subscribeOn(Schedulers.parallel()))
                .expectError(BlockingOperationError.class)
                .verify();
    }
}
```

<!-- file: ejemplo/despues/pom.xml -->
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- Solo para heredar las versiones que fija Spring Boot, entre ellas la de Reactor. Este programa no usa Spring. -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.2</version>
        <relativePath/>
    </parent>

    <groupId>curso.webflux</groupId>
    <artifactId>s03-despues</artifactId>
    <version>1.0.0</version>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-core</artifactId>
        </dependency>
        <dependency>
            <groupId>io.projectreactor.tools</groupId>
            <artifactId>blockhound</artifactId>
            <version>1.0.18.RELEASE</version>              <!-- no la fija Spring Boot -->
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- BlockHound necesita esta opción de la JVM desde el JDK 13. Para el programa, en exec-maven-plugin. -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <argLine>-XX:+AllowRedefinitionToAddDeleteMethods -XX:+EnableDynamicAgentLoading</argLine>
                </configuration>
            </plugin>
            <!-- Se ejecuta en un JVM aparte (exec:exec): BlockHound necesita opciones de la JVM y no puede
                 instrumentar el propio Maven. "mvn compile exec:exec". -->
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.7.0</version>
                <configuration>
                    <executable>${java.home}/bin/java</executable>
                    <arguments>
                        <argument>-XX:+AllowRedefinitionToAddDeleteMethods</argument>
                        <argument>-XX:+EnableDynamicAgentLoading</argument>
                        <argument>-classpath</argument>
                        <classpath/>
                        <argument>Main</argument>
                    </arguments>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

## Errores comunes

- `Mono.just(llamadaBloqueante())` en un manejador: la llamada ocurre en el bucle, antes de que el `Mono` exista.
- `.block()` dentro de una cadena reactiva. En un hilo de Netty, Reactor lo rechaza con `IllegalStateException: block()/blockFirst()/blockLast() are blocking, which is not supported in thread reactor-http-nio-2`. En otros hilos, simplemente bloquea. (Excepción sin valor práctico: `Mono.just(x).block()` o `Mono.fromCallable(...).block()` directos no fallan, porque devuelven el valor sin suscribirse; en cuanto hay un operador detrás, por ejemplo `Mono.just(x).map(...).block()`, Reactor lo rechaza igual. No cuentes con ello.)
- Usar `subscribeOn(boundedElastic())` para todo y llamarlo "reactivo". Tienes un pool de hilos con más pasos.
- `publishOn(Schedulers.parallel())` antes de una llamada bloqueante: mueves el bloqueo a otro hilo que tampoco debe bloquearse.
- Registrar mensajes de forma síncrona en un hilo del bucle con un *appender* que escribe en disco o en red sin búfer. Configúralo asíncrono.
- Usar `Flux.interval` u otra fuente caliente sin decidir qué pasa cuando el consumidor no da abasto.
- Instalar BlockHound en producción.

## Taller

**Objetivo:** encontrar y quitar del bucle de eventos todas las llamadas bloqueantes de un programa, y elegir una estrategia de contrapresión defendible.

Sobre el código del taller de la [sesión 02](../02-reactor/) (tu "confirmar pedido" con Reactor).

1. Sustituye el doble de `Pagos` por uno que use un cliente bloqueante simulado (`Thread.sleep(150)` dentro de un método normal) y llámalo como lo haría alguien con prisa: `Mono.just(pagos.cobrar(pedido))`.
2. Simula el bucle con `Schedulers.newParallel("bucle", 2)`. Lanza 20 confirmaciones y un "ping" a la vez. Mide el total y la espera del ping.
3. Instala BlockHound en las pruebas. Escribe una prueba que confirme un pedido con la suscripción en el bucle y comprueba que falla. Copia el mensaje en `ANALISIS.md`.
4. Corrige con `fromCallable` y `boundedElastic`. Repite las mediciones del paso 2 y la prueba del paso 3.
5. Lleva el número de confirmaciones a 500. Mira cuántos hilos de `boundedElastic` se crearon (sus nombres empiezan por `boundedElastic-`). ¿Qué pasaría con 5.000? Busca el tope en la documentación de `Schedulers` y explica qué ocurre al alcanzarlo.
6. Contrapresión: tu servicio recibe avisos de cambio de existencias de un proveedor, 500 por segundo, y tu proceso tarda 5 ms en procesar cada uno. Modélalo con `Flux.interval`. Elige una estrategia, justifica por qué no se pierde información importante (o por qué se puede perder) y demuéstralo con conteos.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| La prueba con BlockHound falla antes de corregir y pasa después | Ejecución de `mvn test` en los dos estados, en `ANALISIS.md` |
| El ping no espera a los pagos tras la corrección | Mediciones del paso 4 |
| Está explicado qué pasa al llegar al tope de `boundedElastic` | Paso 5 |
| La estrategia de contrapresión tiene una justificación de negocio | Paso 6 |

**Entrega:** rama `taller/webflux-03`, con el código en `taller/solucion/` y `ANALISIS.md`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

Bloquear el bucle de eventos es **el** incidente de los servicios reactivos. Los síntomas son siempre parecidos: latencias que se disparan con carga mientras la CPU está tranquila, *health checks* que fallan sin motivo aparente, y un volcado de hilos (`jcmd <pid> Thread.print`) donde los hilos `reactor-http-nio-*` aparecen en `TIMED_WAITING` o `WAITING` dentro de JDBC, de un cliente HTTP síncrono, de un `synchronized` muy disputado o de una escritura de registros. Los culpables habituales son bibliotecas de terceros que hacen E/S sin decirlo: un SDK de nube, un cliente de caché síncrono, la primera carga de una clase que lee un archivo. Por eso BlockHound se ejecuta en las pruebas de integración de cada servicio reactivo, con las excepciones que se decidan **anotadas una por una** (la sesión 08 tiene un caso real).

## Preguntas senior

1. ¿Qué es la contrapresión y por qué no la resuelve el productor solo?
2. ¿Qué fuentes no pueden respetar la demanda, y qué decisiones te obligan a tomar?
3. ¿Qué diferencia hay entre `subscribeOn` y `publishOn`?
4. ¿Por qué `subscribeOn(boundedElastic())` no convierte una llamada JDBC en no bloqueante?
5. En un volcado de hilos ves los cuatro `reactor-http-nio-*` en `TIMED_WAITING` dentro de `SocketInputStream.read`. ¿Qué concluyes y qué buscas?
6. ¿Por qué BlockHound no se usa en producción, y cómo te aseguras de que lo que detecta se mire?
