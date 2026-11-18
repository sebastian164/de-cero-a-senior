# 02 — Reactor: Mono, Flux y nada pasa hasta suscribir

## Idea

Una receta no es un pastel. Puedes escribir la receta entera (batir, hornear 40 minutos, decorar) sin encender el horno. El pastel solo existe cuando alguien **la ejecuta**. Y si escribes "comprar huevos" en la receta pero vas corriendo a comprarlos mientras la escribes, los compraste aunque nadie vaya a cocinar nunca.

En Reactor, una cadena de operadores es la receta. Suscribirse es cocinar. Casi todos los errores de quien empieza son de estas dos clases: creer que escribir la receta ya cocinó, o cocinar algo mientras escribía la receta.

## Lo esencial

### Mono y Flux

| Tipo | Emite | Equivale, en el mundo bloqueante, a |
|---|---|---|
| `Mono<T>` | Cero o un valor, y termina (o falla) | `T` u `Optional<T>`, que llega más tarde |
| `Flux<T>` | Cero o muchos valores, y termina (o falla) | `List<T>` o `Stream<T>`, que llega poco a poco y puede no terminar nunca |

Los dos son **publicadores** (el `Publisher` de Reactive Streams, el estándar que también usan R2DBC y los drivers reactivos de MongoDB). Una cadena emite tres tipos de señal: `onNext` (un valor), `onComplete` (terminé) y `onError` (fallé). Después de `onComplete` u `onError` no llega nada más.

### Ensamblaje y suscripción

```java
Mono<Movimiento> pendiente = tasas.enCache()           // 1. ensamblaje: se construye la cadena
        .switchIfEmpty(tasas.consultarRemoto())
        .map(tasa -> convertir(tasa))
        .flatMap(repositorio::guardar);

pendiente.subscribe(movimiento -> ...);                 // 2. suscripción: ahora sí ocurre todo
```

| Momento | Qué pasa |
|---|---|
| **Ensamblaje** | Se llaman los métodos que construyen la cadena. Cada operador envuelve al anterior. No se lee, no se consulta, no se guarda nada |
| **Suscripción** | Alguien llama a `subscribe()` (WebFlux lo hace por ti con lo que devuelve el controlador). La suscripción sube por la cadena hasta la fuente, y entonces los datos bajan |

La regla que se deduce: **todo lo que no esté encadenado no ocurre**. Un `Mono` que se crea y se tira es una receta que nadie cocina.

### Los operadores que vas a usar todos los días

| Operador | Qué hace | Ejemplo |
|---|---|---|
| `map` | Transforma el valor, de forma síncrona | `.map(VistaDeCuenta::de)` |
| `flatMap` | Transforma el valor en **otro publicador** y lo encadena | `.flatMap(repositorio::guardar)` |
| `filter` | Deja pasar solo lo que cumple | `.filter(c -> c.saldo() > 0)` |
| `switchIfEmpty` | Si no hubo valor, usa otro publicador | `.switchIfEmpty(Mono.error(...))` |
| `then`, `thenReturn` | Espera a que termine y sigue con otra cosa, ignorando el valor | `.then(registrarMovimiento())` |
| `zip`, `zipWhen` | Combina varios resultados | `Mono.zip(cuenta, riesgo)` |
| `onErrorResume`, `onErrorMap` | Recupera o traduce un error | `.onErrorMap(RiesgoNoDisponible::new)` |
| `timeout`, `retryWhen` | Límite de tiempo y reintentos | `.timeout(Duration.ofSeconds(2))` |
| `doOnNext`, `doOnError` | Efectos laterales que no cambian el flujo: registros, métricas | `.doOnNext(m -> log.info(...))` |

`map` frente a `flatMap` es la primera confusión. Si la función devuelve un valor, es `map`. Si devuelve un `Mono` o un `Flux`, es `flatMap`: con `map` obtendrías un `Mono<Mono<T>>`, y el `Mono` interno nunca se suscribiría.

### Perezoso o ansioso

| Fábrica | Cuándo evalúa su argumento |
|---|---|
| `Mono.just(x)` | **Ya**, al ensamblar. `x` se calculó antes de llamar a `just` |
| `Mono.fromCallable(() -> x())`, `Mono.fromSupplier` | Al suscribirse, cada vez |
| `Mono.defer(() -> crearMono())` | Al suscribirse: construye la cadena en ese momento |
| `Mono.error(e)` | Ya. Para que sea perezoso: `Mono.error(() -> new ...)` |

`Mono.just(llamarAlServicio())` llama al servicio aunque nadie se suscriba, y aunque el `switchIfEmpty` donde está metido no llegue a usarse. Es el segundo error del ejemplo.

### Errores como señales

En código reactivo, un método que devuelve `Mono` no debería lanzar excepciones: debe devolver `Mono.error(...)`. Si lanza, el error ocurre al **ensamblar**, fuera de la cadena, y ningún `onErrorResume` de quien lo llama lo verá. Dentro de un `map` o un `flatMap` sí se puede lanzar: Reactor convierte la excepción en una señal `onError`.

### StepVerifier

Probar un `Mono` con `.block()` funciona para casos simples, pero no deja comprobar la secuencia de señales ni el tiempo. `StepVerifier` (de `reactor-test`) se suscribe por ti y verifica paso a paso:

```java
StepVerifier.create(servicio.registrar("cuenta-1", 10_000))
        .expectNext(new Movimiento("cuenta-1", 40_000_000, "depósito en dólares"))
        .verifyComplete();              // y además, que terminó sin error
```

Con `StepVerifier.withVirtualTime` el reloj de Reactor pasa a ser uno virtual: una prueba de un tiempo límite de dos segundos dura milisegundos. La sesión 08 vuelve sobre las pruebas.

## Ejemplo

Un servicio registra depósitos en dólares: busca la tasa (en caché o, si no la hay, en un servicio remoto), convierte y guarda. Java puro con `reactor-core`; se ejecuta con `mvn -q compile exec:java` desde cada carpeta.

### Antes

<!-- file: ejemplo/antes/src/main/java/ServicioDeMovimientos.java -->
```java
import reactor.core.publisher.Mono;

/** Registra depósitos en dólares. Compila, devuelve lo esperado... y tiene dos errores. */
public final class ServicioDeMovimientos {

    private final RepositorioDeMovimientos repositorio;
    private final ServicioDeTasas tasas;

    public ServicioDeMovimientos(RepositorioDeMovimientos repositorio, ServicioDeTasas tasas) {
        this.repositorio = repositorio;
        this.tasas = tasas;
    }

    public Mono<Movimiento> registrar(String cuentaId, long centavosDeDolar) {
        return tasas.enCache()
                .switchIfEmpty(Mono.just(tasas.consultarRemoto()))    // 1. se llama al ensamblar, haya caché o no
                .map(tasa -> new Movimiento(cuentaId, centavosDeDolar * tasa, "depósito en dólares"))
                .doOnNext(movimiento -> repositorio.guardar(movimiento));   // 2. crea el Mono del guardado y lo tira
    }
}
```

El repositorio y el servicio de tasas, que están bien:

<!-- file: ejemplo/antes/src/main/java/RepositorioDeMovimientos.java -->
```java
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Un repositorio reactivo en memoria. Está bien hecho: guardar no hace nada hasta que alguien se suscribe. */
public final class RepositorioDeMovimientos {

    private final List<Movimiento> guardados = new CopyOnWriteArrayList<>();

    public Mono<Movimiento> guardar(Movimiento movimiento) {
        return Mono.fromCallable(() -> {
            guardados.add(movimiento);
            return movimiento;
        });
    }

    public Flux<Movimiento> todos() {
        return Flux.fromIterable(guardados);
    }

    public int cuantos() {
        return guardados.size();
    }
}
```

<!-- file: ejemplo/antes/src/main/java/ServicioDeTasas.java -->
```java
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Mono;

/** La tasa del dólar. Consultarla "en remoto" es caro; la última conocida queda en caché. */
public final class ServicioDeTasas {

    private final AtomicInteger llamadasRemotas = new AtomicInteger();
    private volatile Long ultimaTasa;

    public Mono<Long> enCache() {
        return Mono.justOrEmpty(ultimaTasa);
    }

    /** Hace de llamada HTTP: cuesta, y se cuenta. */
    public long consultarRemoto() {
        llamadasRemotas.incrementAndGet();
        ultimaTasa = 4_000L;                                     // pesos por dólar
        return ultimaTasa;
    }

    public int llamadasRemotas() {
        return llamadasRemotas.get();
    }
}
```

<!-- file: ejemplo/antes/src/main/java/Main.java -->
```java
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        RepositorioDeMovimientos repositorio = new RepositorioDeMovimientos();
        ServicioDeTasas tasas = new ServicioDeTasas();
        ServicioDeMovimientos servicio = new ServicioDeMovimientos(repositorio, tasas);

        List<Movimiento> devueltos = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            devueltos.add(servicio.registrar("cuenta-" + i, 10_000).block());   // 100 dólares cada uno
        }

        System.out.println("Devueltos por el servicio:     " + devueltos.size() + " movimientos");
        System.out.println("Primero:                       " + devueltos.getFirst());
        System.out.println("Guardados en el repositorio:   " + repositorio.cuantos());
        System.out.println("Llamadas remotas a las tasas:  " + tasas.llamadasRemotas() + " (con la caché llena bastaba 1)");
    }
}
```

```
Devueltos por el servicio:     3 movimientos
Primero:                       Movimiento[cuentaId=cuenta-1, centavos=40000000, concepto=depósito en dólares]
Guardados en el repositorio:   0
Llamadas remotas a las tasas:  3 (con la caché llena bastaba 1)
```

### Análisis

| Lo que se ve | Por qué |
|---|---|
| El servicio devuelve los tres movimientos, bien convertidos | La cadena principal (`enCache`, `switchIfEmpty`, `map`) sí se suscribe: `block()` lo hace |
| No se guardó ninguno | `repositorio.guardar(movimiento)` dentro de `doOnNext` **crea** un `Mono` y lo descarta. Nadie se suscribe a él: es una receta tirada a la basura |
| Tres llamadas remotas, aunque la caché se llenó en la primera | `Mono.just(tasas.consultarRemoto())` ejecuta `consultarRemoto()` **al ensamblar**, cada vez que se llama a `registrar`, haya caché o no. `switchIfEmpty` no la usa, pero la llamada ya ocurrió |

Las dos fallas son invisibles en una prueba que solo mire el valor devuelto. La primera pierde datos sin ningún error. La segunda multiplica el tráfico al servicio de tasas, que es justo lo que la caché quería evitar.

### Después

<!-- file: ejemplo/despues/src/main/java/ServicioDeMovimientos.java -->
```java
import java.time.Duration;
import reactor.core.publisher.Mono;

/** Registra depósitos en dólares. Cada paso está encadenado: nada queda suelto. */
public final class ServicioDeMovimientos {

    static final Duration ESPERA_MAXIMA_TASA = Duration.ofSeconds(2);

    private final RepositorioDeMovimientos repositorio;
    private final ServicioDeTasas tasas;

    public ServicioDeMovimientos(RepositorioDeMovimientos repositorio, ServicioDeTasas tasas) {
        this.repositorio = repositorio;
        this.tasas = tasas;
    }

    public Mono<Movimiento> registrar(String cuentaId, long centavosDeDolar) {
        if (centavosDeDolar <= 0) {
            return Mono.error(new IllegalArgumentException("el valor debe ser mayor que cero"));   // una señal, no un throw
        }
        return tasas.enCache()
                .switchIfEmpty(tasas.consultarRemoto().timeout(ESPERA_MAXIMA_TASA))   // perezoso: solo si la caché está vacía
                .map(tasa -> new Movimiento(cuentaId, centavosDeDolar * tasa, "depósito en dólares"))
                .flatMap(repositorio::guardar);                    // el guardado es parte de la cadena
    }
}
```

<!-- file: ejemplo/despues/src/main/java/ServicioDeTasas.java -->
```java
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Mono;

/** La tasa del dólar. Ahora la consulta remota es perezosa: devolver el Mono no llama a nadie. */
public final class ServicioDeTasas {

    private final AtomicInteger llamadasRemotas = new AtomicInteger();
    private final Duration latencia;
    private volatile Long ultimaTasa;

    public ServicioDeTasas(Duration latencia) {
        this.latencia = latencia;
    }

    public Mono<Long> enCache() {
        return Mono.fromSupplier(() -> ultimaTasa);              // se lee al suscribirse, no al ensamblar
    }

    /** Hace de llamada HTTP no bloqueante. La llamada ocurre al suscribirse; la respuesta llega tras la latencia. */
    public Mono<Long> consultarRemoto() {
        return Mono.fromCallable(() -> {
                    llamadasRemotas.incrementAndGet();
                    return 4_000L;                                // pesos por dólar
                })
                .delayElement(latencia)
                .doOnNext(tasa -> ultimaTasa = tasa);
    }

    public int llamadasRemotas() {
        return llamadasRemotas.get();
    }
}
```

<!-- file: ejemplo/despues/src/main/java/Main.java -->
```java
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import reactor.core.publisher.Mono;

public class Main {

    public static void main(String[] args) {
        RepositorioDeMovimientos repositorio = new RepositorioDeMovimientos();
        ServicioDeTasas tasas = new ServicioDeTasas(Duration.ofMillis(100));
        ServicioDeMovimientos servicio = new ServicioDeMovimientos(repositorio, tasas);

        Mono<Movimiento> pendiente = servicio.registrar("cuenta-1", 10_000);
        System.out.println("Tras ensamblar:  llamadas remotas = " + tasas.llamadasRemotas() + ", guardados = " + repositorio.cuantos());
        Movimiento primero = pendiente.block();
        System.out.println("Tras suscribir:  llamadas remotas = " + tasas.llamadasRemotas() + ", guardados = " + repositorio.cuantos());

        List<Movimiento> devueltos = new ArrayList<>(List.of(primero));
        for (int i = 2; i <= 3; i++) {
            devueltos.add(servicio.registrar("cuenta-" + i, 10_000).block());
        }

        System.out.println("Devueltos por el servicio:     " + devueltos.size() + " movimientos");
        System.out.println("Primero:                       " + devueltos.getFirst());
        System.out.println("Guardados en el repositorio:   " + repositorio.cuantos());
        System.out.println("Llamadas remotas a las tasas:  " + tasas.llamadasRemotas());

        servicio.registrar("cuenta-1", 0)
                .doOnError(error -> System.out.println("Valor cero:                    error -> " + error.getMessage()))
                .onErrorResume(error -> Mono.empty())
                .block();
    }
}
```

```
Tras ensamblar:  llamadas remotas = 0, guardados = 0
Tras suscribir:  llamadas remotas = 1, guardados = 1
Devueltos por el servicio:     3 movimientos
Primero:                       Movimiento[cuentaId=cuenta-1, centavos=40000000, concepto=depósito en dólares]
Guardados en el repositorio:   3
Llamadas remotas a las tasas:  1
Valor cero:                    error -> el valor debe ser mayor que cero
```

Qué cambió:

1. **`flatMap(repositorio::guardar)`** en lugar de `doOnNext`: el guardado es un paso de la cadena; si falla, el error llega a quien llamó.
2. **`consultarRemoto()` es perezoso**: devuelve un `Mono` construido con `fromCallable`, que no llama a nadie hasta que alguien se suscribe. Por eso `switchIfEmpty(tasas.consultarRemoto())` ya es correcto: ensamblar la alternativa no la ejecuta.
3. **`enCache()` lee al suscribirse** (`fromSupplier`), no al ensamblar (`justOrEmpty`). En la versión anterior daba igual porque se usaba enseguida; en una cadena que se guarda y se reutiliza, no.
4. **Un valor inválido es una señal de error**, no un `throw`.
5. Un **tiempo límite** sobre la llamada remota: nada externo debe poder colgar una cadena para siempre.

Las pruebas, con StepVerifier. `mvn test` las ejecuta:

<!-- file: ejemplo/despues/src/test/java/ServicioDeMovimientosTest.java -->
```java
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class ServicioDeMovimientosTest {

    private final RepositorioDeMovimientos repositorio = new RepositorioDeMovimientos();
    private final ServicioDeTasas tasas = new ServicioDeTasas(Duration.ofMillis(10));
    private final ServicioDeMovimientos servicio = new ServicioDeMovimientos(repositorio, tasas);

    @Test
    void sinSuscripcionNoPasaNada() {
        servicio.registrar("cuenta-1", 10_000);                    // ensamblado y descartado

        assertEquals(0, tasas.llamadasRemotas());
        assertEquals(0, repositorio.cuantos());
    }

    @Test
    void registraConvierteYGuarda() {
        StepVerifier.create(servicio.registrar("cuenta-1", 10_000))
                .expectNext(new Movimiento("cuenta-1", 40_000_000, "depósito en dólares"))
                .verifyComplete();

        StepVerifier.create(repositorio.todos())
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void conLaCacheLlenaNoVuelveALlamar() {
        StepVerifier.create(servicio.registrar("cuenta-1", 100).then(servicio.registrar("cuenta-2", 100)))
                .expectNextCount(1)
                .verifyComplete();

        assertEquals(1, tasas.llamadasRemotas());
    }

    @Test
    void unValorCeroEsUnaSenalDeError() {
        StepVerifier.create(servicio.registrar("cuenta-1", 0))
                .expectErrorMessage("el valor debe ser mayor que cero")
                .verify();
    }

    @Test
    void siLasTasasTardanMasDeDosSegundosSeCorta() {
        // Tiempo virtual: la prueba dura milisegundos aunque "pasen" dos segundos.
        StepVerifier.withVirtualTime(() -> new ServicioDeMovimientos(repositorio, new ServicioDeTasas(Duration.ofMinutes(1)))
                        .registrar("cuenta-1", 10_000))
                .expectSubscription()
                .expectNoEvent(Duration.ofMillis(1_999))
                .thenAwait(Duration.ofMillis(1))
                .expectError(TimeoutException.class)
                .verify(Duration.ofSeconds(1));

        assertEquals(0, repositorio.cuantos());
    }
}
```

```
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
```

La última prueba comprueba un tiempo límite de dos segundos frente a un servicio que tarda un minuto, y termina en milisegundos: con tiempo virtual, `thenAwait` adelanta el reloj en lugar de esperar.

## Errores comunes

- Llamar a un método que devuelve `Mono` o `Flux` y no usar el resultado. El compilador no avisa; el dato no se guarda.
- `doOnNext(x -> repositorio.guardar(x))`: el mismo error, más escondido.
- `subscribe()` dentro de un `map` o un `flatMap` para "lanzar" algo: rompe la cadena, se pierden los errores y nadie espera a que termine.
- `Mono.just(llamadaCostosa())` donde hacía falta `fromCallable` o `defer`, sobre todo dentro de `switchIfEmpty`, `onErrorResume` o `defaultIfEmpty`.
- `map` con una función que devuelve `Mono`: el resultado es `Mono<Mono<T>>` y el interno no se ejecuta.
- Lanzar excepciones desde un método que devuelve `Mono`, en lugar de `Mono.error(...)`.
- Probar con `.subscribe(v -> assertEquals(...))`: la afirmación corre en otro hilo, quizá después de que la prueba terminó. La sesión 08 lo demuestra.

## Taller

**Objetivo:** escribir con Reactor el caso de uso "confirmar pedido" de tu servicio, sin bloquear y sin perder pasos.

Sobre tu servicio de pedidos del [módulo hexagonal](../../hexagonal/). No hace falta Spring: puertos en memoria que devuelven `Mono` y `Flux`, como el repositorio del ejemplo.

1. Define los puertos `Inventario.reservar(pedido): Mono<Reserva>`, `Pagos.cobrar(pedido): Mono<Cobro>` y `Pedidos.guardar(pedido): Mono<Pedido>`, con implementaciones en memoria que tarden lo que indiques (`delayElement`).
2. Escribe `confirmar(pedidoId): Mono<Pedido>`: busca el pedido, reserva, cobra y guarda. Si el pedido no existe, la cadena termina con tu excepción de dominio.
3. Haz que la reserva y una consulta de antifraude (otro puerto) ocurran **en paralelo**, y que el cobro empiece solo cuando las dos terminaron bien.
4. Añade un tiempo límite de 3 s al cobro. Si se supera, la cadena falla con una excepción tuya, no con `TimeoutException`.
5. Escribe pruebas con StepVerifier para: el caso feliz; pedido inexistente; antifraude rechaza (el cobro **no** debe llamarse: cuéntalo); el cobro supera el tiempo límite (con tiempo virtual: la prueba debe durar menos de un segundo).
6. Escribe una prueba que demuestre que llamar a `confirmar` sin suscribirse no reserva ni cobra nada.
7. Busca en tu código cualquier `Mono.just(...)` cuyo argumento haga trabajo. Justifica cada uno que quede.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| Ningún paso queda fuera de la cadena | La prueba del paso 6 y una revisión de `doOnNext` y `subscribe` |
| Reserva y antifraude van en paralelo | Una prueba de tiempo: el total ronda el máximo de los dos, no la suma |
| Si antifraude rechaza, no se cobra | Contador en el doble de `Pagos` |
| La prueba del tiempo límite usa tiempo virtual | Dura menos de un segundo |
| No hay `block()` fuera de las pruebas | Búsqueda en el código |

**Entrega:** rama `taller/webflux-02`, con el código en `taller/solucion/` y un `ANALISIS.md` con lo que encontraste en el paso 7.

## En el trabajo real

Los dos errores del ejemplo son los más frecuentes en revisiones de código reactivo, y casi nunca rompen una prueba unitaria superficial: rompen en producción, como datos que "a veces no se guardan" o como un tráfico inexplicable a un servicio que tenía caché. Las herramientas de análisis estático (algunas reglas de IntelliJ, Error Prone) avisan de un `Mono` cuyo resultado se ignora; vale la pena activarlas. En equipos con experiencia, la regla de revisión es simple: todo `Mono` o `Flux` creado debe acabar devuelto o encadenado, y todo `Mono.just` se mira dos veces.

## Preguntas senior

1. ¿Qué diferencia hay entre ensamblar una cadena y suscribirse a ella? Da un ejemplo de error que venga de confundirlas.
2. ¿Por qué `Mono.just(x)` dentro de `switchIfEmpty` puede ser un error de rendimiento y no solo de estilo?
3. ¿Cuándo usarías `map` y cuándo `flatMap`? ¿Qué pasa si te equivocas?
4. ¿Por qué un método que devuelve `Mono` no debería lanzar excepciones?
5. ¿Qué aporta StepVerifier frente a `.block()` en una prueba? ¿Y el tiempo virtual?
6. ¿Quién se suscribe a lo que devuelve un controlador de WebFlux?
