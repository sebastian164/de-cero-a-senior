# 04 — Agotamiento del pool: conexiones retenidas de más

## Idea

La única fotocopiadora de la oficina. Sacar una copia tarda diez segundos. Pero alguien pone su documento, se acuerda de que le falta una firma, y se va a buscarla al otro piso dejando la tapa abierta con su papel adentro. La máquina no está rota ni la está usando nadie; está **ocupada**. La fila crece detrás de una persona que no está copiando nada.

## Lo esencial

En una fuga, la conexión no vuelve nunca. Aquí sí vuelve, pero **tarde**: el código la retiene mientras hace otra cosa. El efecto en un pico de tráfico es el mismo: `Connection is not available`.

### La cuenta

```
conexiones necesarias ≈ peticiones por segundo × segundos que cada una retiene la conexión
```

Con 100 peticiones por segundo que retienen la conexión 20 ms, bastan 2 conexiones. Si la retienen 500 ms, hacen falta 50. El tráfico es el mismo; lo que cambió es cuánto tiempo se queda cada una con la conexión. **Reducir ese tiempo rinde más que añadir conexiones.**

### Quién retiene conexiones sin usarlas

| Causa | Qué pasa |
|---|---|
| **Una llamada externa dentro de una transacción** | Se abre la transacción, se consulta, se llama por HTTP a otro servicio y luego se guarda. La conexión está tomada durante toda la llamada, y el día que ese servicio va lento, tu pool se vacía |
| **`@Transactional` demasiado amplio** | La anotación en un método que hace mucho más que hablar con la base |
| **Vista abierta durante toda la petición** | En Spring con JPA, `spring.jpa.open-in-view` viene activado: la conexión puede quedar retenida hasta que termina de escribirse la respuesta, incluida la serialización |
| **Consultas lentas** | Falta un índice, y cada consulta retiene la conexión segundos |
| **Esperas de bloqueos** | Una transacción espera a que otra libere una fila |
| **Procesar dentro de la transacción** | Generar un PDF, recorrer un millón de filas, esperar un `sleep` |
| **Consultas en bucle** | Mil consultas pequeñas donde bastaba una |

### La regla

Una conexión se toma **lo más tarde posible** y se devuelve **lo más pronto posible**. Dentro de una transacción solo va lo que tiene que ser atómico con la base. Las llamadas a otros servicios van **fuera**: antes o después.

Eso obliga a pensar qué pasa si la llamada externa falla después de haber confirmado, que es justo el tema de [outbox y sagas](../../kafka-y-sagas/).

### Cómo se reconoce

| Señal | Qué indica |
|---|---|
| Conexiones activas pegadas al máximo durante los picos, y que bajan después | Retención, no fuga |
| Hilos esperando una conexión (pendientes) mayor que cero | El pool es el cuello de botella |
| Tiempo de uso de la conexión mucho mayor que el de las consultas | La conexión se retiene sin usarla |
| La base tiene la CPU baja mientras la aplicación da errores de pool | El problema no es la base |

La última es la más reveladora: si la base está descansada y tú no consigues conexión, las conexiones están tomadas por código que no está consultando.

## Ejemplo

Doce peticiones a la vez contra un pool de cuatro conexiones, con medio segundo de espera máxima. Cada petición consulta (10 ms), llama a un servicio externo (300 ms) y guarda (10 ms). Java puro; ejecuta cada uno con `java Main.java`.

### Antes

<!-- file: ejemplo/antes/Main.java -->
```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** La llamada externa ocurre con la conexión tomada. */
public class Main {

    static final Semaphore pool = new Semaphore(4, true);       // cuatro conexiones
    static final AtomicInteger atendidas = new AtomicInteger();
    static final AtomicInteger sinConexion = new AtomicInteger();

    public static void main(String[] args) throws InterruptedException {
        List<Callable<Void>> peticiones = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            peticiones.add(() -> {
                confirmarPedido();
                return null;
            });
        }
        ExecutorService hilos = Executors.newFixedThreadPool(12);
        hilos.invokeAll(peticiones);
        hilos.shutdown();

        System.out.println("Pool de 4 conexiones, 12 peticiones a la vez, espera máxima de 500 ms");
        System.out.println("Atendidas:             " + atendidas.get());
        System.out.println("Sin conexión a tiempo: " + sinConexion.get());
    }

    static void confirmarPedido() throws InterruptedException {
        if (!pool.tryAcquire(500, TimeUnit.MILLISECONDS)) {
            sinConexion.incrementAndGet();                       // "Connection is not available"
            return;
        }
        try {
            Thread.sleep(10);                                    // consulta el pedido
            Thread.sleep(300);                                   // llama al servicio de pagos, con la conexión tomada
            Thread.sleep(10);                                    // guarda el resultado
            atendidas.incrementAndGet();
        } finally {
            pool.release();
        }
    }
}
```

### Análisis

| Lo que se ve | Por qué |
|---|---|
| Unas cuatro peticiones se quedan sin conexión | Cada una retiene la conexión 320 ms. Las cuatro primeras la tienen hasta entonces; las cuatro siguientes, hasta los 640 ms. Las últimas llevan ya más de 500 ms esperando |
| La base apenas trabajó | De los 320 ms, solo 20 fueron consultas. El 94 % del tiempo la conexión estuvo tomada sin usarse |
| No hay ninguna fuga | Todas las conexiones se devuelven. Un detector de fugas no diría nada |

Y esto es con el servicio de pagos respondiendo en 300 ms. El día que tarde tres segundos, no se atiende casi nadie: un problema ajeno tumba tu servicio a través del pool.

### Después

<!-- file: ejemplo/despues/Main.java -->
```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** La conexión se toma solo para hablar con la base; la llamada externa va sin ella. */
public class Main {

    static final Semaphore pool = new Semaphore(4, true);
    static final AtomicInteger atendidas = new AtomicInteger();
    static final AtomicInteger sinConexion = new AtomicInteger();

    public static void main(String[] args) throws InterruptedException {
        List<Callable<Void>> peticiones = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            peticiones.add(() -> {
                confirmarPedido();
                return null;
            });
        }
        ExecutorService hilos = Executors.newFixedThreadPool(12);
        hilos.invokeAll(peticiones);
        hilos.shutdown();

        System.out.println("Pool de 4 conexiones, 12 peticiones a la vez, espera máxima de 500 ms");
        System.out.println("Atendidas:             " + atendidas.get());
        System.out.println("Sin conexión a tiempo: " + sinConexion.get());
    }

    static void confirmarPedido() throws InterruptedException {
        if (!conConexion(10)) {                                  // 1. consulta el pedido y devuelve la conexión
            return;
        }
        Thread.sleep(300);                                       // 2. llama al servicio de pagos, sin conexión
        if (!conConexion(10)) {                                  // 3. guarda el resultado en otra transacción corta
            return;
        }
        atendidas.incrementAndGet();
    }

    /** Toma una conexión, trabaja con la base ese tiempo y la devuelve. */
    static boolean conConexion(long milisegundosDeTrabajo) throws InterruptedException {
        if (!pool.tryAcquire(500, TimeUnit.MILLISECONDS)) {
            sinConexion.incrementAndGet();
            return false;
        }
        try {
            Thread.sleep(milisegundosDeTrabajo);
            return true;
        } finally {
            pool.release();
        }
    }
}
```

- Las doce peticiones se atienden con el mismo pool de cuatro.
- Cada conexión se retiene 10 ms en lugar de 320.
- Si el servicio de pagos tarda tres segundos, las peticiones tardan más, pero el pool sigue libre para el resto del sistema.

El precio: ya no hay una sola transacción. Si el paso 3 falla después de que pagos cobró, hay que tener una respuesta para eso. Esa respuesta existe, y es la segunda mitad de la ruta de Kafka y sagas.

## Errores comunes

- Subir el tamaño del pool hasta que "deje de fallar".
- `@Transactional` en el método más externo "por si acaso".
- Llamadas HTTP, envíos de correo o publicación de mensajes dentro de una transacción.
- No saber que la vista abierta está activada.
- Mirar solo el tiempo de las consultas y no el tiempo que se retiene la conexión.
- Culpar a la base cuando la base está ociosa.
- Hilos virtuales con el mismo pool pequeño y sin entender que ahora el pool es el único límite: miles de hilos esperando una de diez conexiones.

## Taller

**Objetivo:** reproducir el agotamiento con Spring Boot y PostgreSQL, diagnosticarlo con métricas y arreglarlo sin tocar el tamaño del pool.

**Sobre tu servicio hexagonal:** si hiciste el [módulo de arquitectura hexagonal](../../hexagonal/), reproduce el agotamiento con un caso de uso de tu servicio de pedidos que llame a algo lento dentro de su unidad de trabajo. El arreglo es de diseño: sacar esa llamada, no agrandar el pool. La [sesión 06](../06-el-pool-en-un-servicio-hexagonal/) lo muestra con el banco.

Con el [entorno](../entorno/), un pool de cinco y un servicio externo simulado que tarda lo que tú le indiques.

1. Escribe "confirmar pedido" con `@Transactional` alrededor de todo: leer el pedido, llamar al servicio externo y guardar.
2. Con el servicio externo en 50 ms, lanza 20 usuarios concurrentes. Anota peticiones por segundo, errores, conexiones activas, pendientes y tiempo de uso de la conexión.
3. Sube el servicio externo a 2 s. Repite. Mira a la vez la CPU de PostgreSQL. Describe lo que ves.
4. Aplica la cuenta: con tu tráfico y tu tiempo de retención, ¿cuántas conexiones harían falta? ¿Las tienes?
5. Reestructura: transacciones cortas alrededor de la base y la llamada externa fuera. **No cambies el tamaño del pool.** Repite los pasos 2 y 3.
6. Ahora que no hay una sola transacción: haz que el guardado final falle después de una llamada externa exitosa. ¿En qué estado queda el sistema? Describe cómo lo resolverías; no hace falta implementarlo.
7. **La vista abierta.** Con JPA y `open-in-view` en su valor por defecto, haz un extremo que lea una entidad y luego tarde un segundo en construir la respuesta. Mide cuánto se retiene la conexión. Desactívalo y repite. ¿Qué se rompe al desactivarlo y cómo lo arreglas?
8. **Consulta lenta.** Quita un índice para que una consulta pase de 5 ms a 800 ms. ¿Cómo se ve en las métricas del pool? ¿En qué se diferencia del caso de la llamada externa?
9. **Bloqueos.** Dos peticiones que actualizan la misma fila, una de ellas con una pausa dentro de la transacción. ¿Qué hace la otra y qué le pasa al pool?
10. **Hilos virtuales.** Activa los hilos virtuales en Spring Boot y repite el paso 3. ¿Qué cambia en el número de hilos esperando y en el comportamiento del pool? ¿Sirve de algo para este problema?
11. Para cada una de las cuatro causas que reprodujiste (llamada externa, vista abierta, consulta lenta, bloqueo), escribe qué combinación de métricas la delata.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| El servicio aguanta el servicio externo lento con el mismo tamaño de pool | Paso 5, con mediciones antes y después |
| Cada causa tiene su huella en las métricas | La tabla del paso 11 |
| La decisión sobre la vista abierta está justificada | Paso 7 |
| Está escrito qué se pierde al partir la transacción | Paso 6 |

**Entrega:** rama `taller/hikari-04`, con el código, las mediciones y `ANALISIS.md` en `taller/solucion/`.

## En el trabajo real

El incidente clásico: un servicio de terceros se pone lento, y de repente fallan pantallas que no tienen nada que ver con él. La conexión era el recurso compartido, y una funcionalidad que la retenía durante la llamada externa dejó sin conexiones a todas las demás. La corrección casi nunca está en el pool: está en dónde empieza y dónde termina cada transacción.

## Preguntas senior

1. Diferencia entre una fuga y una retención excesiva.
2. ¿Por qué no se hacen llamadas externas dentro de una transacción?
3. ¿Qué hace la vista abierta durante la petición y cuándo la desactivarías?
4. La aplicación da errores de pool y la base tiene la CPU al 5 %. ¿Qué concluyes?
5. ¿Qué cambia con hilos virtuales respecto al pool de conexiones?
