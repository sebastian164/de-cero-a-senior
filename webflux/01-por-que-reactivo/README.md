# 01 — Por qué reactivo (y cuándo no)

## Idea

Un restaurante con veinte meseros. Cada mesero atiende una mesa: toma el pedido, lo lleva a la cocina y **se queda de pie junto a la ventanilla** hasta que el plato sale. Si la cocina tarda diez minutos, el mesero pasa diez minutos sin hacer nada. Con veinte mesas ocupadas, la mesa veintiuno espera aunque todos los meseros estén quietos.

Hay dos arreglos. El primero: que el mesero deje el pedido, se vaya a atender otras mesas y la cocina **toque una campana** cuando el plato esté listo. Con tres meseros atiendes cien mesas. Eso es el modelo reactivo. El segundo: contratar meseros que no cobran mientras esperan, tantos como mesas haya. Eso son los hilos virtuales de Java 21.

Los dos arreglos sirven para lo mismo: **dejar de pagar por esperar**. Ninguno hace que la cocina cocine más rápido.

## Lo esencial

### Bloquear y no bloquear

| | Bloqueante | No bloqueante |
|---|---|---|
| Qué hace el hilo mientras llega la respuesta | Duerme. Sigue ocupado aunque no trabaje | Queda libre. Alguien le avisa cuando hay respuesta |
| Cuántos hilos necesita una espera de 200 ms | Uno, durante 200 ms | Ninguno durante la espera |
| Cómo se escribe | `respuesta = llamar()` | `llamar().map(respuesta -> ...)`: dices qué hacer cuando llegue |
| Bibliotecas | JDBC, JPA, `RestClient`, casi todo Java clásico | R2DBC, `WebClient`, drivers reactivos de MongoDB y Redis |

Un servidor clásico (Tomcat con Spring MVC) usa **un hilo por petición**, de un pool de unos doscientos. Si cada petición pasa el 95 % de su tiempo esperando a la base o a otro servicio, el límite no es la CPU: es el número de hilos. Cada hilo de plataforma cuesta memoria (su pila) y cambios de contexto del sistema operativo, así que no se pueden tener cien mil.

### El bucle de eventos

Un servidor reactivo (Netty, que es lo que usa WebFlux) tiene **muy pocos hilos**, normalmente uno por núcleo, llamados el **bucle de eventos** (*event loop*). Cada hilo hace lo mismo una y otra vez: "¿qué conexión tiene algo listo? Atiéndela un momento y pasa a la siguiente". Nadie espera dormido: cuando una operación de red termina, el sistema operativo avisa y el bucle continúa el trabajo pendiente.

Esto tiene una consecuencia que recorre todo el módulo: **un hilo del bucle no se puede bloquear nunca**. Si uno de cuatro hilos se queda esperando a JDBC, una cuarta parte del servidor se detiene para todas las conexiones que tenía asignadas. La sesión 03 lo provoca y lo mide.

### Programación reactiva

Escribir código que no bloquea a mano (con *callbacks*) se vuelve ilegible enseguida. La programación reactiva lo ordena: describes una **cadena de pasos sobre datos que van a llegar** (`Mono` para cero o un valor, `Flux` para muchos), y una biblioteca (Reactor, en Spring) la ejecuta cuando los datos llegan. Añade dos cosas que los callbacks no tienen: errores como parte de la cadena y **contrapresión**, la forma en que el consumidor dice "no me mandes más de lo que puedo procesar" (sesión 03).

### La comparación honesta con los hilos virtuales

Java 21 trajo los hilos virtuales (lo viste en la [sesión de concurrencia del Nivel 6](../../java/nivel-06-java-avanzado/04-concurrencia/)): hilos que la JVM gestiona y que, al bloquearse en E/S, **sueltan** el hilo de plataforma que los ejecutaba. Puedes tener cien mil y escribir código bloqueante normal. Resuelven el mismo problema que el modelo reactivo, con código mucho más simple.

| | Reactivo (WebFlux, Reactor) | Hilos virtuales (MVC + `spring.threads.virtual.enabled`) |
|---|---|---|
| Esperas de E/S sin gastar hilos | Sí | Sí |
| Código | Cadenas de operadores; hay que aprender un modelo nuevo | Código imperativo de siempre |
| Trazas de error y depuración | Difíciles: la pila no cuenta la historia | Normales |
| Bibliotecas | Solo las no bloqueantes (R2DBC, WebClient...) | Todas, incluidas JDBC y JPA |
| Contrapresión y flujos (streaming, SSE, WebSocket) | Integrados | A mano |
| Combinar muchas llamadas concurrentes con tiempos límite | Natural (`zip`, `timeout`, `retryWhen`) | Posible con concurrencia estructurada, todavía en evolución |
| Trampas | Bloquear en el bucle de eventos | Recursos limitados que ahora se agotan antes (el pool de conexiones), `synchronized` que fija el hilo de plataforma en Java 21 (corregido en JDK 24) |

### Cuándo no conviene lo reactivo

- **Trabajo de CPU.** Calcular no es esperar: el ejemplo lo mide.
- **Un servicio CRUD con JPA y tráfico moderado.** MVC con hilos virtuales da casi toda la escalabilidad sin reescribir nada.
- **Un equipo que no conoce el modelo.** Un `.block()` mal puesto o una llamada JDBC escondida anula todo el beneficio y es difícil de ver en una revisión.
- **Cuando la base de datos es el cuello de botella.** Si PostgreSQL acepta veinte conexiones, atender diez mil peticiones a la vez solo cambia dónde esperan.

Y cuándo sí: pasarelas y servicios que sobre todo llaman a otros servicios, flujos de datos largos (eventos, SSE, WebSocket), mucha concurrencia con poca memoria, y equipos que ya dominan el modelo.

## Ejemplo

Doscientas peticiones a la vez. Cada una consulta un servicio de riesgo que tarda 200 ms. Sin red: la espera se simula, para aislar el mecanismo. Cada versión es un proyecto Maven; se ejecuta con `mvn -q compile exec:java` desde su carpeta.

### Antes

Un hilo por petición, con un pool fijo de veinte hilos de plataforma, como un servidor clásico con su pool de trabajadores.

<!-- file: ejemplo/antes/src/main/java/Main.java -->
```java
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Un hilo por petición, con un pool fijo de hilos de plataforma: el modelo clásico de un servidor como Tomcat.
 * Cada petición consulta el servicio de riesgo, que tarda 200 ms en responder. Mientras espera, el hilo no hace nada.
 */
public class Main {

    static final int PETICIONES = 200;
    static final int HILOS = 20;                                  // el pool del servidor
    static final long LATENCIA_RIESGO_MS = 200;

    static final Set<String> hilosUsados = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) throws Exception {
        long inicio = System.nanoTime();
        List<Future<String>> respuestas = new ArrayList<>();
        try (ExecutorService servidor = Executors.newFixedThreadPool(HILOS)) {
            for (int i = 1; i <= PETICIONES; i++) {
                String cuenta = "cuenta-" + i;
                respuestas.add(servidor.submit(() -> atender(cuenta)));
            }
            for (Future<String> respuesta : respuestas) {
                respuesta.get();
            }
        }
        long ms = (System.nanoTime() - inicio) / 1_000_000;

        System.out.printf("%d peticiones, cada una espera %d ms al servicio de riesgo%n", PETICIONES, LATENCIA_RIESGO_MS);
        System.out.printf("Pool de %d hilos de plataforma: %d ms en total, %d hilos usados%n", HILOS, ms, hilosUsados.size());
        System.out.printf("Tiempo de CPU útil por petición: casi cero. Tiempo de hilo ocupado: %d ms%n", LATENCIA_RIESGO_MS);
    }

    static String atender(String cuenta) throws InterruptedException {
        hilosUsados.add(Thread.currentThread().getName());
        return consultarRiesgo(cuenta);
    }

    /** Hace de llamada HTTP bloqueante: el hilo se queda dormido hasta que llega la respuesta. */
    static String consultarRiesgo(String cuenta) throws InterruptedException {
        Thread.sleep(LATENCIA_RIESGO_MS);
        return cuenta + ": BAJO";
    }
}
```

```
200 peticiones, cada una espera 200 ms al servicio de riesgo
Pool de 20 hilos de plataforma: 2042 ms en total, 20 hilos usados
Tiempo de CPU útil por petición: casi cero. Tiempo de hilo ocupado: 200 ms
```

### Análisis

| Lo que se ve | Por qué |
|---|---|
| 2 segundos para 200 peticiones de 200 ms | Diez tandas de veinte. La petición 200 esperó 1,8 s **a que hubiera un hilo libre**, no al servicio de riesgo |
| La CPU no hizo casi nada | Los veinte hilos pasaron el 99 % del tiempo dormidos |
| Más hilos lo "arreglarían" | Hasta cierto punto: con 10.000 peticiones harían falta 10.000 hilos de plataforma, con su memoria y sus cambios de contexto |

El código no tiene ningún error de lógica. El problema es de **modelo**: atar un recurso escaso (el hilo) a algo que pasa la mayor parte del tiempo esperando.

### Después

La misma carga resuelta de tres formas, más un escenario de CPU para ver dónde no hay ganancia. El `pom.xml` añade `reactor-core`, en la versión que fija Spring Boot.

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
    <artifactId>s01-despues</artifactId>
    <version>1.0.0</version>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-core</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.7.0</version>
                <configuration>
                    <mainClass>Main</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

<!-- file: ejemplo/despues/src/main/java/Main.java -->
```java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * La misma carga resuelta de tres formas, y medida. Dos escenarios: esperar a otro servicio (E/S) y calcular (CPU).
 * La conclusión honesta no es "reactivo siempre": es "no bloquear un hilo escaso mientras se espera".
 */
public class Main {

    static final long LATENCIA_RIESGO_MS = 200;
    static final int NUCLEOS = Runtime.getRuntime().availableProcessors();

    static final Set<Long> hilos = ConcurrentHashMap.newKeySet();
    static int iteracionesPorCalculo;

    public static void main(String[] args) throws Exception {
        System.out.printf("Escenario 1: 200 peticiones que esperan %d ms al servicio de riesgo (E/S)%n", LATENCIA_RIESGO_MS);
        medir("Pool de 20 hilos de plataforma, bloqueando", () -> conPool(20, 200, Main::consultarRiesgoBloqueando));
        medir("Reactor, sin bloquear", () -> conReactor(200));
        medir("Hilos virtuales, bloqueando", () -> conHilosVirtuales(200, Main::consultarRiesgoBloqueando));

        System.out.println();
        System.out.println("Escenario 2: 10.000 peticiones iguales (el pool de 20 tardaría 100 s: no se mide)");
        medir("Reactor, sin bloquear", () -> conReactor(10_000));
        medir("Hilos virtuales, bloqueando", () -> conHilosVirtuales(10_000, Main::consultarRiesgoBloqueando));

        calibrarCalculo();
        System.out.println();
        System.out.printf("Escenario 3: 200 peticiones que calculan unos 20 ms de CPU cada una (%d núcleos)%n", NUCLEOS);
        medir("Pool de hilos de plataforma (uno por núcleo)", () -> conPool(NUCLEOS, 200, Main::calcularFirma));
        medir("Reactor, Schedulers.parallel()", () -> conReactorCalculando(200));
        medir("Hilos virtuales", () -> conHilosVirtuales(200, Main::calcularFirma));
    }

    // ---- Las tres formas de atender ----

    static void conPool(int tamano, int peticiones, Function<String, String> atender) throws Exception {
        try (ExecutorService servidor = Executors.newFixedThreadPool(tamano)) {
            esperarTodas(servidor, peticiones, atender);
        }
    }

    static void conHilosVirtuales(int peticiones, Function<String, String> atender) throws Exception {
        try (ExecutorService servidor = Executors.newVirtualThreadPerTaskExecutor()) {   // un hilo virtual por petición
            esperarTodas(servidor, peticiones, atender);
        }
    }

    /** Nadie espera dormido: la petición deja dicho qué hacer cuando llegue la respuesta, y el hilo queda libre. */
    static void conReactor(int peticiones) {
        Flux.range(1, peticiones)
                .flatMap(i -> consultarRiesgoSinBloquear("cuenta-" + i), peticiones)
                .blockLast();                                     // solo main espera, para poder medir
    }

    static void conReactorCalculando(int peticiones) {
        Flux.range(1, peticiones)
                .parallel(NUCLEOS)
                .runOn(Schedulers.parallel())
                .map(i -> calcularFirma("cuenta-" + i))
                .sequential()
                .blockLast();
    }

    // ---- Lo que hace cada petición ----

    /** Hace de llamada HTTP bloqueante: el hilo duerme hasta que llega la respuesta. */
    static String consultarRiesgoBloqueando(String cuenta) {
        hilos.add(Thread.currentThread().threadId());
        try {
            Thread.sleep(LATENCIA_RIESGO_MS);
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupcion);
        }
        return cuenta + ": BAJO";
    }

    /** Hace de llamada HTTP no bloqueante: un temporizador avisa cuando "llega" la respuesta. */
    static Mono<String> consultarRiesgoSinBloquear(String cuenta) {
        return Mono.delay(Duration.ofMillis(LATENCIA_RIESGO_MS))
                .map(tick -> {
                    hilos.add(Thread.currentThread().threadId());
                    return cuenta + ": BAJO";
                });
    }

    /** Trabajo de CPU puro: nadie espera a nadie, solo se calcula. */
    static String calcularFirma(String cuenta) {
        hilos.add(Thread.currentThread().threadId());
        byte[] valor = cuenta.getBytes(StandardCharsets.UTF_8);
        MessageDigest sha = sha256();
        for (int i = 0; i < iteracionesPorCalculo; i++) {
            valor = sha.digest(valor);
        }
        return cuenta + ":" + valor[0];
    }

    // ---- Medición ----

    interface Carga {
        void ejecutar() throws Exception;
    }

    static void medir(String nombre, Carga carga) throws Exception {
        hilos.clear();
        long inicio = System.nanoTime();
        carga.ejecutar();
        long ms = (System.nanoTime() - inicio) / 1_000_000;
        System.out.printf("  %-44s %6d ms  %6d hilos distintos%n", nombre, ms, hilos.size());
    }

    static void esperarTodas(ExecutorService servidor, int peticiones, Function<String, String> atender) throws Exception {
        List<Future<String>> respuestas = new ArrayList<>();
        for (int i = 1; i <= peticiones; i++) {
            String cuenta = "cuenta-" + i;
            Callable<String> peticion = () -> atender.apply(cuenta);
            respuestas.add(servidor.submit(peticion));
        }
        for (Future<String> respuesta : respuestas) {
            respuesta.get();
        }
    }

    /** Ajusta cuántas vueltas da el cálculo para que dure unos 20 ms en esta máquina. */
    static void calibrarCalculo() {
        iteracionesPorCalculo = 20_000;
        for (int i = 0; i < 5; i++) {
            calcularFirma("calentamiento");                       // que el JIT compile antes de medir
        }
        long inicio = System.nanoTime();
        calcularFirma("calibrar");
        double ms = (System.nanoTime() - inicio) / 1_000_000.0;
        iteracionesPorCalculo = (int) (iteracionesPorCalculo * 20 / ms);
    }

    static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException imposible) {
            throw new IllegalStateException(imposible);
        }
    }
}
```

Salida en un equipo de 18 núcleos (los tiempos cambian un poco en cada ejecución y de una máquina a otra):

```
Escenario 1: 200 peticiones que esperan 200 ms al servicio de riesgo (E/S)
  Pool de 20 hilos de plataforma, bloqueando     2041 ms      20 hilos distintos
  Reactor, sin bloquear                           243 ms      18 hilos distintos
  Hilos virtuales, bloqueando                     215 ms     200 hilos distintos

Escenario 2: 10.000 peticiones iguales (el pool de 20 tardaría 100 s: no se mide)
  Reactor, sin bloquear                           273 ms      18 hilos distintos
  Hilos virtuales, bloqueando                     225 ms   10000 hilos distintos

Escenario 3: 200 peticiones que calculan unos 20 ms de CPU cada una (18 núcleos)
  Pool de hilos de plataforma (uno por núcleo)    386 ms      18 hilos distintos
  Reactor, Schedulers.parallel()                  275 ms      18 hilos distintos
  Hilos virtuales                                 245 ms     200 hilos distintos
```

Cómo leerlo:

- **Escenario 1.** Reactor atiende las 200 en lo que tarda una (200 ms más un poco), y los avisos llegan por los hilos de `Schedulers.parallel()`, uno por núcleo: nadie esperó dormido. Los hilos virtuales consiguen lo mismo con el código bloqueante de siempre; los 200 hilos son virtuales y corren sobre unos pocos de plataforma.
- **Escenario 2.** Con 10.000 peticiones el tiempo apenas cambia en las dos versiones que no atan un hilo de plataforma a cada espera.
- **Escenario 3.** Las tres dan lo mismo, más o menos: el límite son los núcleos. Lo reactivo **no hace más rápido el cálculo**. Las diferencias entre las tres filas son ruido de medición (el calentamiento del JIT, el orden de ejecución), no una ventaja de un modelo.

La conclusión de esta sesión no es "usa WebFlux". Es que el problema se llama **bloquear un hilo escaso mientras se espera**, que hay dos soluciones serias, y que elegir entre ellas es una decisión de diseño con costos.

## Errores comunes

- Adoptar WebFlux "porque es más rápido". No lo es: rinde más **por hilo** cuando hay mucha espera.
- Medir con una sola petición. Las dos versiones tardan 200 ms; la diferencia solo aparece con concurrencia.
- Poner WebFlux y seguir usando JDBC, JPA o `RestTemplate` debajo: se pierde la ventaja y se añade el riesgo de bloquear el bucle (sesiones 03 y 05).
- Esperar que lo reactivo acelere un trabajo de CPU.
- Ignorar los hilos virtuales al decidir. Desde Java 21 son la alternativa por defecto para servicios bloqueantes.
- Olvidar que el cuello de botella puede estar en otro sitio: el pool de conexiones, la base, el servicio externo.

## Taller

**Objetivo:** decidir con mediciones si tu servicio de pedidos del [módulo hexagonal](../../hexagonal/) ganaría algo con un modelo reactivo.

1. Escribe un programa como el del ejemplo con tu caso: "confirmar pedido" consulta inventario (80 ms), consulta pagos (150 ms) y guarda (10 ms). Simula las esperas.
2. Mide 500 confirmaciones concurrentes con un pool de 50 hilos de plataforma, con hilos virtuales y con Reactor. En la versión con Reactor, las dos consultas deben ir **en paralelo** (pista: `Mono.zip`). Haz lo mismo en las otras dos versiones si puedes.
3. Repite con 5.000 confirmaciones. ¿Qué versión cambia más? ¿Por qué?
4. Añade un paso de CPU de 30 ms (firmar el pedido). Repite el paso 2. ¿Qué cambió en la comparación?
5. Ahora introduce un límite realista: solo 20 confirmaciones pueden estar en "guardar" a la vez (un `Semaphore` en las versiones con hilos; `flatMap` con concurrencia limitada en la de Reactor). ¿Qué versión gana ahora y por qué?
6. Escribe una recomendación de una página para tu equipo: MVC con hilos virtuales o WebFlux, para este servicio. Apóyala en tus números y en la tabla de esta sesión.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| Las tres versiones producen el mismo resultado funcional | Mismas confirmaciones en la salida |
| Las consultas independientes van en paralelo en la versión reactiva | El tiempo de una confirmación ronda 160 ms, no 240 |
| El efecto del límite del paso 5 está explicado | `ANALISIS.md` |
| La recomendación cita mediciones, no opiniones | `ANALISIS.md` |

**Entrega:** rama `taller/webflux-01`, con el código en `taller/solucion/` y la recomendación en `ANALISIS.md`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

La pregunta que se hace en una revisión de arquitectura no es "¿reactivo sí o no?", sino "¿dónde pasa el tiempo una petición?". Si la respuesta es "esperando a otros servicios" y el volumen es alto, hay que dejar de atar hilos a las esperas. Desde Java 21, lo habitual en un servicio Spring nuevo con JPA es MVC con hilos virtuales. WebFlux aparece en pasarelas (Spring Cloud Gateway es reactivo), en servicios de agregación que llaman a muchos otros, en streaming y en equipos que ya lo dominan. Lo que casi nunca sale bien es la mezcla: WebFlux por fuera y bloqueo por dentro.

## Preguntas senior

1. ¿Qué recurso se agota primero en un servidor de un hilo por petición cuando los servicios de los que depende se vuelven lentos?
2. ¿Por qué un hilo del bucle de eventos no puede bloquearse nunca, y qué ocurre si lo hace?
3. ¿Qué cambia y qué no cambia con los hilos virtuales respecto al modelo reactivo?
4. Un servicio pasa el 90 % del tiempo de cada petición calculando. ¿Le ayuda WebFlux? ¿Y los hilos virtuales?
5. Tu servicio usa JPA y tiene un pool de 20 conexiones. ¿Qué ganarías migrando a WebFlux? ¿Y a hilos virtuales?
6. ¿Qué te haría elegir WebFlux en un servicio nuevo en 2026?
